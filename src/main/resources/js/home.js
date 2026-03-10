document.addEventListener('DOMContentLoaded', function () {
        console.log('🚀 SmartGram загружен');

        const storiesContainer = document.getElementById('storiesContainer');
        const postsContainer   = document.getElementById('postsContainer');
        const addStoryBtn      = document.getElementById('addStoryBtn');
        const storyModal       = document.getElementById('storyModal');
        const closeStoryBtn    = document.getElementById('closeStory');
        const reactionsPanel   = document.getElementById('reactionsPanel');

        let myUserId     = null;
        let followingIds = new Set();
        let storyTimer   = null;

        const likedPosts = new Set(JSON.parse(localStorage.getItem('likedPosts') || '[]'));
        function saveLikedPosts() { localStorage.setItem('likedPosts', JSON.stringify([...likedPosts])); }

        // ── INIT ──
        (async function init() {
            try {
                const meRes = await fetch('/api/auth/me', { credentials: 'include' });
                if (meRes.ok) { const me = await meRes.json(); myUserId = me.id || me.userId; }
            } catch (e) { console.warn(e); }

            if (myUserId) {
                try {
                    const fRes = await fetch(`/api/subscriptions/${myUserId}/followings`, { credentials: 'include' });
                    if (fRes.ok) {
                        const list = await fRes.json();
                        list.forEach(f => {
                            const id = f.followingUserId || f.targetUserId || f.userId || f.id;
                            if (id) followingIds.add(String(id));
                        });
                    }
                } catch (e) { console.warn(e); }
            }

            loadStories();
            loadPosts();
        })();

        // ── STORIES ──
        async function loadStories() {
            try {
                const res = await fetch('/api/stories', { credentials: 'include' });
                if (!res.ok) return;
                renderStories(await res.json());
            } catch (e) { console.error(e); }
        }

        function renderStories(stories) {
            const byUser = {};
            stories.forEach(s => { if (!byUser[s.userId]) byUser[s.userId] = []; byUser[s.userId].push(s); });

            const uids = Object.keys(byUser).filter(id => id !== myUserId);
            uids.sort((a, b) => {
                const af = followingIds.has(a), bf = followingIds.has(b);
                return af === bf ? 0 : af ? -1 : 1;
            });

            uids.forEach(async uid => {
                const profile = await getProfile(uid);
                storiesContainer.appendChild(createStoryElement(uid, profile, byUser[uid]));
            });
        }

        function createStoryElement(uid, profile, userStories) {
            const item = document.createElement('div');
            item.className = 'story-item';
            item.dataset.uid = uid;

            const isFollowing = followingIds.has(String(uid));
            const latestStory = userStories[0];
            const badgeHtml = !isFollowing ? `<div class="story-follow-badge"><i class="fas fa-plus"></i></div>` : '';

            const avatarSrc = profile?.avatarUrl || '/images/default-avatar.png';
            const name      = profile?.name || '?';

            item.innerHTML = `
                <div class="story-avatar-wrap">
                    <div class="story-ring">
                        <div class="story-ring-inner">
                            <img class="story-avatar-img" src="${avatarSrc}" alt="${name}"
                                 onerror="this.src='/images/default-avatar.png'">
                        </div>
                    </div>
                    ${badgeHtml}
                </div>
                <span class="story-username">${name}</span>
            `;

            item.querySelector('.story-ring').addEventListener('click', e => {
                if (e.target.closest('.story-follow-badge')) return;
                openStory(latestStory, userStories, profile);
            });

            const badge = item.querySelector('.story-follow-badge');
            if (badge) {
                badge.addEventListener('click', async e => {
                    e.stopPropagation();
                    try {
                        const res = await fetch(`/api/subscriptions/${uid}/subscribe`, { method: 'POST', credentials: 'include' });
                        const ok  = res.ok || res.status === 409;
                        if (ok) {
                            followingIds.add(String(uid));
                            badge.remove();
                            document.querySelectorAll(`.follow-btn[data-uid="${uid}"]`).forEach(b => b.remove());
                        }
                    } catch (err) { console.error(err); }
                });
            }
            return item;
        }

        // ── STORY VIEWER ──
        function openStory(story, allStories, profile) {
            document.getElementById('storyImage').src          = story.photoUrl;
            document.getElementById('storyAvatarImg').src      = profile?.avatarUrl || '/images/default-avatar.png';
            document.getElementById('storyUsernameModal').textContent = profile?.name || '?';
            document.getElementById('storyTime').textContent   = formatTime(story.createdAt);

            const bar = document.getElementById('storyProgress');
            bar.style.width = '0%';
            storyModal.classList.add('show');
            markStoryViewed(story.id);

            if (storyTimer) clearInterval(storyTimer);
            let p = 0;
            storyTimer = setInterval(() => {
                p += 0.5; bar.style.width = p + '%';
                if (p >= 100) { clearInterval(storyTimer); closeStoryModal(); }
            }, 30);

            document.querySelectorAll('.reaction-btn').forEach(btn => {
                btn.onclick = () => reactToStory(story.id, btn.dataset.reaction);
            });
        }

        function closeStoryModal() {
            storyModal.classList.remove('show');
            if (storyTimer) { clearInterval(storyTimer); storyTimer = null; }
        }

        closeStoryBtn.addEventListener('click', closeStoryModal);
        storyModal.addEventListener('click', e => { if (e.target === storyModal) closeStoryModal(); });

        async function markStoryViewed(id) {
            try { await fetch(`/api/stories/${id}/view`, { method: 'POST', credentials: 'include' }); } catch {}
        }
        async function reactToStory(id, reaction) {
            try {
                await fetch(`/api/stories/${id}/reaction`, {
                    method: 'POST', credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ reaction })
                });
            } catch {}
        }

        // ── POSTS ──
        async function loadPosts() {
            try {
                const res = await fetch('/api/posts/feed', { credentials: 'include' });
                if (!res.ok) throw new Error();
                renderPosts(await res.json());
            } catch {
                postsContainer.innerHTML = `<p style="text-align:center;color:var(--muted);padding:40px">Пока нет постов</p>`;
            }
        }

        async function renderPosts(posts) {
            postsContainer.innerHTML = '';
            if (!posts.length) {
                postsContainer.innerHTML = `<p style="text-align:center;color:var(--muted);padding:40px">Пока нет постов в ленте</p>`;
                return;
            }
            for (const post of posts) {
                const profile = await getProfile(post.userId);
                postsContainer.appendChild(await createPostElement(post, profile));
            }
        }

        async function createPostElement(post, profile) {
            const card = document.createElement('article');
            card.className = 'post-card';
            card.dataset.postId = post.id;

            const isOwn       = String(post.userId) === String(myUserId);
            const isFollowing = followingIds.has(String(post.userId));
            const reactions   = await getReactionsCount(post.id);
            const isLiked     = likedPosts.has(post.id);

            card.innerHTML = `
                <div class="post-header">
                    <div class="post-author" onclick="window.location.href='/profile/${post.userId}'">
                        <img class="post-avatar"
                             src="${profile?.avatarUrl || '/images/default-avatar.png'}"
                             alt="${profile?.name || '?'}"
                             onerror="this.src='/images/default-avatar.png'">
                        <div>
                            <div class="post-author-name">${profile?.name || '?'} ${profile?.surname || ''}</div>
                            <div class="post-time">${formatTime(post.createdAt)}</div>
                        </div>
                    </div>
                    ${!isOwn && !isFollowing ? `<button class="follow-btn" data-uid="${post.userId}">Подписаться</button>` : ''}
                </div>
                <img class="post-image" src="${post.photoUrl}" alt=""
                     onerror="this.style.display='none'">
                <div class="post-content">
                    ${post.description ? `<p class="post-description">${post.description}</p>` : ''}
                    <div class="post-actions">
                        <button class="action-btn like-btn ${isLiked ? 'liked' : ''}" data-post-id="${post.id}">
                            <i class="fas fa-heart"></i>
                            <span class="action-count">${reactions}</span>
                        </button>
                        <button class="action-btn" onclick="window.location.href='/post/${post.id}'">
                            <i class="fas fa-comment"></i>
                            <span class="action-count">${post.commentsCount || 0}</span>
                        </button>
                        <button class="action-btn"><i class="fas fa-share-nodes"></i></button>
                        <button class="action-btn" style="margin-left:auto"><i class="fas fa-bookmark"></i></button>
                    </div>
                </div>
            `;

            // Follow
            const followBtn = card.querySelector('.follow-btn');
            if (followBtn) {
                followBtn.addEventListener('click', async () => {
                    const uid = followBtn.dataset.uid;
                    const res = await fetch(`/api/subscriptions/${uid}/subscribe`, { method: 'POST', credentials: 'include' });
                    if (res.ok || res.status === 409) {
                        followingIds.add(String(uid));
                        document.querySelectorAll(`.follow-btn[data-uid="${uid}"]`).forEach(b => b.remove());
                        document.querySelectorAll(`.story-item[data-uid="${uid}"] .story-follow-badge`).forEach(b => b.remove());
                    }
                });
            }

            // Like
            const likeBtn = card.querySelector('.like-btn');
            let rt;
            likeBtn.addEventListener('mouseenter', () => { rt = setTimeout(() => showReactionsPanel(post.id, likeBtn), 500); });
            likeBtn.addEventListener('mouseleave', () => clearTimeout(rt));
            likeBtn.addEventListener('click', () => toggleReaction(post.id, 'LIKE', likeBtn));

            return card;
        }

        // ── REACTIONS ──
        function showReactionsPanel(postId, button) {
            reactionsPanel.classList.add('show');
            const r = button.getBoundingClientRect();
            reactionsPanel.style.left = Math.max(10, r.left) + 'px';
            reactionsPanel.style.transform = 'none';
            reactionsPanel.querySelectorAll('.reaction-option').forEach(btn => {
                btn.onclick = () => { reactToPost(postId, btn.dataset.reaction); reactionsPanel.classList.remove('show'); };
            });
        }

        document.addEventListener('click', e => {
            if (!reactionsPanel.contains(e.target) && !e.target.closest('.like-btn'))
                reactionsPanel.classList.remove('show');
        });

        async function toggleReaction(postId, reaction, button) {
            const res = await fetch(`/api/posts/${postId}/reaction`, {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ reaction })
            });
            if (res.ok) {
                const cnt = button.querySelector('.action-count');
                if (likedPosts.has(postId)) {
                    likedPosts.delete(postId); button.classList.remove('liked');
                    cnt.textContent = Math.max(0, parseInt(cnt.textContent) - 1);
                } else {
                    likedPosts.add(postId); button.classList.add('liked');
                    cnt.textContent = parseInt(cnt.textContent) + 1;
                }
                saveLikedPosts();
            }
        }

        async function reactToPost(postId, reaction) {
            const res = await fetch(`/api/posts/${postId}/reaction`, {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ reaction })
            });
            if (res.ok && !likedPosts.has(postId)) {
                likedPosts.add(postId); saveLikedPosts();
                const lb = document.querySelector(`.like-btn[data-post-id="${postId}"]`);
                if (lb) { lb.classList.add('liked'); lb.querySelector('.action-count').textContent = parseInt(lb.querySelector('.action-count').textContent) + 1; }
            }
        }

        // ── HELPERS ──
        async function getProfile(uid) {
            try {
                const res = await fetch(`/api/profiles/user/${uid}`, { credentials: 'include' });
                return res.ok ? await res.json() : null;
            } catch { return null; }
        }

        async function getReactionsCount(postId) {
            try {
                const res = await fetch(`/api/posts/${postId}/reactions/count`, { credentials: 'include' });
                return res.ok ? await res.json() : 0;
            } catch { return 0; }
        }

        function formatTime(ts) {
            if (!ts) return '';
            const d = Math.floor((Date.now() - new Date(ts)) / 1000);
            if (d < 60)    return 'только что';
            if (d < 3600)  return Math.floor(d/60) + ' мин';
            if (d < 86400) return Math.floor(d/3600) + ' ч';
            return Math.floor(d/86400) + ' д';
        }

        addStoryBtn.addEventListener('click', () => window.location.href = '/stories/create');

        console.log('✅ Инициализация завершена');
    });