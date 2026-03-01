document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 SmartGram загружен');

    // Элементы
    const storiesContainer = document.getElementById('storiesContainer');
    const postsContainer = document.getElementById('postsContainer');
    const addStoryBtn = document.getElementById('addStoryBtn');
    const storyModal = document.getElementById('storyModal');
    const closeStory = document.getElementById('closeStory');
    const reactionsPanel = document.getElementById('reactionsPanel');

    // Данные пользователя
    const userData = JSON.parse(localStorage.getItem('user') || '{}');
    const userId = userData.id;
    const token = localStorage.getItem('token');

    // Загрузка историй и постов
    loadStories();
    loadPosts();

    // === ЗАГРУЗКА ИСТОРИЙ ===
    async function loadStories() {
        try {
            const response = await fetch('/api/stories', {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });

            if (!response.ok) {
                console.error('Ошибка загрузки историй');
                return;
            }

            const stories = await response.json();
            renderStories(stories);
        } catch (error) {
            console.error('Ошибка:', error);
        }
    }

    function renderStories(stories) {
        // Группировка по пользователям
        const storiesByUser = {};
        stories.forEach(story => {
            if (!storiesByUser[story.userId]) {
                storiesByUser[story.userId] = [];
            }
            storiesByUser[story.userId].push(story);
        });

        // Рендеринг
        Object.keys(storiesByUser).forEach(async (userId) => {
            const userStories = storiesByUser[userId];
            const latestStory = userStories[0];

            // Получение профиля пользователя
            const profile = await getProfile(userId);

            const storyElement = createStoryElement(profile, latestStory, userStories);
            storiesContainer.appendChild(storyElement);
        });
    }

    function createStoryElement(profile, story, allStories) {
        const storyItem = document.createElement('div');
        storyItem.className = 'story-item';

        const hasStory = story !== null;

        storyItem.innerHTML = `
            <div class="story-avatar ${hasStory ? '' : 'no-story'}">
                <img src="${profile.avatarUrl || '/images/default-avatar.png'}" alt="${profile.name}">
            </div>
            <span class="story-username">${profile.name}</span>
        `;

        if (hasStory) {
            storyItem.addEventListener('click', () => openStory(story, allStories, profile));
        }

        return storyItem;
    }

    // === ПРОСМОТР ИСТОРИИ ===
    function openStory(story, allStories, profile) {
        const storyImage = document.getElementById('storyImage');
        const storyAvatarImg = document.getElementById('storyAvatarImg');
        const storyUsernameModal = document.getElementById('storyUsernameModal');
        const storyTime = document.getElementById('storyTime');
        const storyProgress = document.getElementById('storyProgress');

        storyImage.src = story.photoUrl;
        storyAvatarImg.src = profile.avatarUrl || '/images/default-avatar.png';
        storyUsernameModal.textContent = profile.name;
        storyTime.textContent = formatTime(story.createdAt);

        storyModal.classList.add('show');

        // Отметка просмотра
        markStoryViewed(story.id);

        // Прогресс бар
        let progress = 0;
        const interval = setInterval(() => {
            progress += 0.5;
            storyProgress.style.width = progress + '%';

            if (progress >= 100) {
                clearInterval(interval);
                closeStoryModal();
            }
        }, 30); // 6 секунд на историю

        // Реакции на историю
        document.querySelectorAll('.reaction-btn').forEach(btn => {
            btn.onclick = () => reactToStory(story.id, btn.dataset.reaction);
        });
    }

    function closeStoryModal() {
        storyModal.classList.remove('show');
    }

    closeStory.addEventListener('click', closeStoryModal);

    async function markStoryViewed(storyId) {
        try {
            await fetch(`/api/stories/${storyId}/view`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });
        } catch (error) {
            console.error('Ошибка отметки просмотра:', error);
        }
    }

    async function reactToStory(storyId, reaction) {
        try {
            await fetch(`/api/stories/${storyId}/reaction`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ reaction })
            });
            console.log('Реакция добавлена');
        } catch (error) {
            console.error('Ошибка добавления реакции:', error);
        }
    }

    // === ЗАГРУЗКА ПОСТОВ ===
    async function loadPosts() {
        try {
            const response = await fetch('/api/posts/feed', {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });

            if (!response.ok) {
                throw new Error('Ошибка загрузки постов');
            }

            const posts = await response.json();
            renderPosts(posts);
        } catch (error) {
            console.error('Ошибка:', error);
            postsContainer.innerHTML = '<p style="text-align: center; color: rgba(255,255,255,0.5);">Не удалось загрузить посты</p>';
        }
    }

    async function renderPosts(posts) {
        postsContainer.innerHTML = '';

        if (posts.length === 0) {
            postsContainer.innerHTML = '<p style="text-align: center; color: rgba(255,255,255,0.5);">Пока нет постов</p>';
            return;
        }

        for (const post of posts) {
            const profile = await getProfile(post.userId);
            const postElement = await createPostElement(post, profile);
            postsContainer.appendChild(postElement);
        }
    }

    async function createPostElement(post, profile) {
        const postCard = document.createElement('article');
        postCard.className = 'post-card';
        postCard.dataset.postId = post.id;

        const isFollowing = await checkFollowing(post.userId);
        const reactionsCount = await getReactionsCount(post.id);

        postCard.innerHTML = `
            <div class="post-header">
                <div class="post-author" onclick="window.location.href='/profile/${post.userId}'">
                    <img src="${profile.avatarUrl || '/images/default-avatar.png'}" alt="${profile.name}" class="post-avatar">
                    <div class="post-author-info">
                        <span class="post-author-name">${profile.name} ${profile.surname}</span>
                        <span class="post-time">${formatTime(post.createdAt)}</span>
                    </div>
                </div>
                ${post.userId !== userId ? `
                    <button class="follow-btn ${isFollowing ? 'following' : ''}" onclick="toggleFollow('${post.userId}', this)">
                        ${isFollowing ? 'Отписаться' : 'Подписаться'}
                    </button>
                ` : ''}
            </div>

            <img src="${post.photoUrl}" alt="Post" class="post-image">

            <div class="post-content">
                ${post.description ? `<p class="post-description">${post.description}</p>` : ''}

                <div class="post-actions">
                    <button class="action-btn like-btn" data-post-id="${post.id}">
                        <i class="fas fa-heart"></i>
                        <span class="action-count">${reactionsCount}</span>
                    </button>
                    <button class="action-btn" onclick="window.location.href='/post/${post.id}'">
                        <i class="fas fa-comment"></i>
                        <span class="action-count">${post.commentsCount || 0}</span>
                    </button>
                    <button class="action-btn">
                        <i class="fas fa-share"></i>
                    </button>
                    <button class="action-btn">
                        <i class="fas fa-bookmark"></i>
                    </button>
                </div>
            </div>
        `;

        // Обработчик лайка с панелью реакций
        const likeBtn = postCard.querySelector('.like-btn');
        let reactionTimeout;

        likeBtn.addEventListener('mouseenter', () => {
            reactionTimeout = setTimeout(() => {
                showReactionsPanel(post.id, likeBtn);
            }, 500);
        });

        likeBtn.addEventListener('mouseleave', () => {
            clearTimeout(reactionTimeout);
        });

        likeBtn.addEventListener('click', () => {
            toggleReaction(post.id, 'LIKE', likeBtn);
        });

        return postCard;
    }

    // === ПАНЕЛЬ РЕАКЦИЙ ===
    function showReactionsPanel(postId, button) {
        reactionsPanel.classList.add('show');
        reactionsPanel.style.left = button.getBoundingClientRect().left + 'px';

        reactionsPanel.querySelectorAll('.reaction-option').forEach(btn => {
            btn.onclick = () => {
                reactToPost(postId, btn.dataset.reaction);
                reactionsPanel.classList.remove('show');
            };
        });
    }

    document.addEventListener('click', (e) => {
        if (!reactionsPanel.contains(e.target) && !e.target.closest('.like-btn')) {
            reactionsPanel.classList.remove('show');
        }
    });

    async function toggleReaction(postId, reaction, button) {
        try {
            const response = await fetch(`/api/posts/${postId}/reaction`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ reaction })
            });

            if (response.ok) {
                button.classList.toggle('liked');
                const count = button.querySelector('.action-count');
                count.textContent = parseInt(count.textContent) + (button.classList.contains('liked') ? 1 : -1);
            }
        } catch (error) {
            console.error('Ошибка реакции:', error);
        }
    }

    async function reactToPost(postId, reaction) {
        try {
            await fetch(`/api/posts/${postId}/reaction`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ reaction })
            });
            console.log('Реакция добавлена:', reaction);
        } catch (error) {
            console.error('Ошибка:', error);
        }
    }

    // === ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ===
    async function getProfile(userId) {
        try {
            const response = await fetch(`/api/profiles/user/${userId}`, {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });
            return await response.json();
        } catch (error) {
            console.error('Ошибка загрузки профиля:', error);
            return { name: 'Unknown', avatarUrl: null };
        }
    }

    async function getReactionsCount(postId) {
        try {
            const response = await fetch(`/api/posts/${postId}/reactions/count`, {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });
            return await response.json();
        } catch (error) {
            return 0;
        }
    }

    async function checkFollowing(userId) {
        // Реализовать проверку подписки
        return false;
    }

    window.toggleFollow = async function(userId, button) {
        // Реализовать подписку/отписку
        button.classList.toggle('following');
        button.textContent = button.classList.contains('following') ? 'Отписаться' : 'Подписаться';
    };

    function formatTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = Math.floor((now - date) / 1000);

        if (diff < 60) return 'Только что';
        if (diff < 3600) return Math.floor(diff / 60) + ' мин назад';
        if (diff < 86400) return Math.floor(diff / 3600) + ' ч назад';
        return Math.floor(diff / 86400) + ' д назад';
    }

    // === ДОБАВЛЕНИЕ ИСТОРИИ ===
    addStoryBtn.addEventListener('click', () => {
        // Перенаправление на страницу создания истории
        window.location.href = '/stories/create';
    });

    console.log('✅ Инициализация завершена');
});