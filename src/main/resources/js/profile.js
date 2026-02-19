document.addEventListener('DOMContentLoaded', async function () {
    console.log('🚀 Загрузка профиля');

    let userId;
    let currentProfileId; // сохраняем profileId для загрузки аватара

    try {
        const meResponse = await fetch('/api/auth/me', { credentials: 'include' });
        if (!meResponse.ok) { window.location.href = '/auth/login'; return; }
        const me = await meResponse.json();
        userId = me.id;
        console.log('✅ userId:', userId);
    } catch (e) {
        window.location.href = '/auth/login';
        return;
    }

    await Promise.all([loadProfile(userId), loadStories(userId), loadPosts(userId)]);

    // Вешаем клик на аватар ПОСЛЕ загрузки (userId уже есть)
    setupAvatarUpload();

    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
            btn.classList.add('active');
            document.getElementById(btn.dataset.tab + '-tab').classList.add('active');
        });
    });

    // === ЗАГРУЗКА ПРОФИЛЯ ===
    async function loadProfile(userId) {
        try {
            const response = await fetch(`/api/profiles/user/${userId}`, { credentials: 'include' });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const profile = await response.json();
            console.log('✅ Профиль:', profile);
            currentProfileId = profile.id; // сохраняем для аватара
            renderProfile(profile);
        } catch (e) {
            console.error('Ошибка профиля:', e);
        }
    }

    // === ЗАГРУЗКА ПОСТОВ ===
    async function loadPosts(userId) {
        try {
            const response = await fetch(`/api/posts/user/${userId}`, { credentials: 'include' });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const posts = await response.json();
            renderPosts(posts);
        } catch (e) {
            console.error('Ошибка постов:', e);
            document.getElementById('postsGrid').innerHTML = `
                <div class="empty-state" style="grid-column:1/-1">
                    <i class="fas fa-camera"></i><p>Постов пока нет</p>
                </div>`;
        }
    }

    // === ЗАГРУЗКА ИСТОРИЙ ===
    async function loadStories(userId) {
        try {
            const response = await fetch(`/api/stories/user/${userId}`, { credentials: 'include' });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const stories = await response.json();
            renderStories(stories);
        } catch (e) {
            console.error('Ошибка историй:', e);
        }
    }

    // === РЕНДЕР ПРОФИЛЯ ===
    function renderProfile(profile) {
        const avatar = document.getElementById('profileAvatar');
        avatar.src = profile.avatarUrl && profile.avatarUrl !== 'null'
            ? profile.avatarUrl
            : '/images/default-avatar.png';

        document.getElementById('profileUsername').textContent =
            `${profile.name.toLowerCase()}_${profile.surname.toLowerCase()}`;
        document.getElementById('profileName').textContent = `${profile.name} ${profile.surname}`;
        document.getElementById('profileBio').textContent = profile.bioDescription || '';
        document.getElementById('postsCount').textContent = profile.postsIds || 0;
        document.getElementById('followersCount').textContent = profile.followersIds || 0;
        document.getElementById('followingCount').textContent = profile.followingIds || 0;

        if (profile.location)
            document.getElementById('profileLocation').innerHTML =
                `<i class="fas fa-map-marker-alt"></i> ${formatLocation(profile.location)}`;
        if (profile.birthDate)
            document.getElementById('profileBirthdate').innerHTML =
                `<i class="fas fa-birthday-cake"></i> ${calculateAge(profile.birthDate)} лет`;
        if (profile.relationStatus)
            document.getElementById('profileRelation').innerHTML =
                `<i class="fas fa-heart"></i> ${formatRelationStatus(profile.relationStatus)}`;
    }

    // === РЕНДЕР ИСТОРИЙ ===
    function renderStories(stories) {
        const grid = document.getElementById('storiesGrid');
        const addBtn = grid.querySelector('.add-highlight');
        grid.innerHTML = '';
        grid.appendChild(addBtn);
        if (!stories?.length) return;
        stories.forEach(story => {
            const el = document.createElement('div');
            el.className = 'highlight-item has-story';
            el.innerHTML = `
                <div class="highlight-circle">
                    <img src="${story.photoUrl || '/images/default-avatar.png'}" alt="Story">
                </div>
                <span class="highlight-name">${formatStoryTime(story.createdAt)}</span>`;
            grid.appendChild(el);
        });
    }

    // === РЕНДЕР ПОСТОВ ===
    function renderPosts(posts) {
        const grid = document.getElementById('postsGrid');
        grid.innerHTML = '';
        if (!posts?.length) {
            grid.innerHTML = `<div class="empty-state" style="grid-column:1/-1">
                <i class="fas fa-camera"></i><p>Постов пока нет</p></div>`;
            return;
        }
        posts.forEach(post => {
            const el = document.createElement('div');
            el.className = 'post-grid-item';
            el.innerHTML = `
                <img src="${post.photoUrl}" alt="Post">
                <div class="post-grid-overlay">
                    <div class="overlay-stat"><i class="fas fa-heart"></i><span>${post.reactionsCount || 0}</span></div>
                    <div class="overlay-stat"><i class="fas fa-comment"></i><span>${post.commentsCount || 0}</span></div>
                </div>`;
            el.onclick = () => openPost(post);
            grid.appendChild(el);
        });
    }

    // === ОТКРЫТИЕ ПОСТА ===
    function openPost(post) {
        document.getElementById('modalPostImage').src = post.photoUrl;
        document.getElementById('modalUserAvatar').src = document.getElementById('profileAvatar').src;
        document.getElementById('modalUsername').textContent = document.getElementById('profileUsername').textContent;
        document.getElementById('modalPostTime').textContent = formatPostTime(post.createdAt);
        document.getElementById('modalDescription').textContent = post.description || '';
        document.getElementById('modalLikes').textContent = post.reactionsCount || 0;
        document.getElementById('modalComments').textContent = post.commentsCount || 0;
        document.getElementById('postModal').classList.add('show');
    }

    window.closePostModal = () => document.getElementById('postModal').classList.remove('show');

    // === ЗАГРУЗКА АВАТАРА (клик на фото) ===
    function setupAvatarUpload() {
        const avatarEl = document.getElementById('profileAvatar');
        avatarEl.style.cursor = 'pointer';
        avatarEl.title = 'Нажми чтобы изменить фото';

        avatarEl.addEventListener('click', () => {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = 'image/png,image/jpeg';
            input.onchange = async (e) => {
                const file = e.target.files[0];
                if (!file) return;

                if (!currentProfileId) {
                    console.error('profileId не найден');
                    return;
                }

                const formData = new FormData();
                formData.append('file', file);

                try {
                    const res = await fetch(`/api/profiles/${currentProfileId}/avatar`, {
                        method: 'POST',
                        credentials: 'include',
                        body: formData
                    });

                    if (!res.ok) throw new Error(`HTTP ${res.status}`);
                    const data = await res.json();
                    avatarEl.src = data.avatarUrl;
                    console.log('✅ Аватар обновлён:', data.avatarUrl);
                } catch (err) {
                    console.error('Ошибка загрузки аватара:', err);
                }
            };
            input.click();
        });
    }

    // === ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ===
    function formatLocation(l) {
        return {
            'USA': '🇺🇸 США', 'RUSSIA': '🇷🇺 Россия', 'UK': '🇬🇧 Великобритания',
            'GERMANY': '🇩🇪 Германия', 'FRANCE': '🇫🇷 Франция', 'ITALY': '🇮🇹 Италия',
            'SPAIN': '🇪🇸 Испания', 'CANADA': '🇨🇦 Канада', 'UKRAINE': '🇺🇦 Украина'
        }[l] || l;
    }

    function formatRelationStatus(s) {
        return {
            'SINGLE': 'Не женат/Не замужем', 'IN_A_RELATIONSHIP': 'В отношениях',
            'ENGAGED': 'Помолвлен(а)', 'MARRIED': 'Женат/Замужем', 'IT_IS_COMPLICATED': 'Всё сложно'
        }[s] || s;
    }

    function calculateAge(birthDate) {
        const today = new Date(), birth = new Date(birthDate);
        let age = today.getFullYear() - birth.getFullYear();
        if (today.getMonth() < birth.getMonth() ||
            (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate())) age--;
        return age;
    }

    function formatStoryTime(ts) {
        const diff = Math.floor((new Date() - new Date(ts)) / 3600000);
        return diff < 1 ? 'Только что' : diff < 24 ? `${diff}ч` : `${Math.floor(diff / 24)}д`;
    }

    function formatPostTime(ts) {
        const diff = Math.floor((new Date() - new Date(ts)) / 1000);
        if (diff < 60) return 'Только что';
        if (diff < 3600) return `${Math.floor(diff / 60)} мин. назад`;
        if (diff < 86400) return `${Math.floor(diff / 3600)} ч. назад`;
        const d = Math.floor(diff / 86400);
        return d === 1 ? 'Вчера' : d < 7 ? `${d} дней назад` : new Date(ts).toLocaleDateString('ru-RU');
    }

    console.log('✅ Профиль инициализирован');
});