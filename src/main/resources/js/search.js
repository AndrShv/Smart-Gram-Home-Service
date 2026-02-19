  document.addEventListener('DOMContentLoaded', function() {
    const searchInput = document.getElementById('searchInput');
    const clearBtn = document.getElementById('clearBtn');
    const tabs = document.querySelectorAll('.tab');
    const usersGrid = document.getElementById('usersGrid');
    const postsGrid = document.getElementById('postsGrid');
    const loading = document.getElementById('loading');
    const token = localStorage.getItem('token');

    let searchTimeout;

    // Очистка поиска
    clearBtn.addEventListener('click', () => {
        searchInput.value = '';
        clearBtn.style.display = 'none';
        usersGrid.innerHTML = '<div class="empty-state"><i class="fas fa-user-astronaut"></i><p>Начните поиск</p></div>';
    });

    searchInput.addEventListener('input', (e) => {
        const value = e.target.value;
        clearBtn.style.display = value ? 'block' : 'none';

        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            if (value.length >= 2) {
                performSearch(value);
            }
        }, 300);
    });

    // Переключение табов
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');

            document.querySelectorAll('.tab-content').forEach(content => {
                content.classList.remove('active');
            });

            const tabId = tab.dataset.tab + '-tab';
            document.getElementById(tabId).classList.add('active');
        });
    });

    async function performSearch(query) {
        loading.style.display = 'block';

        try {
            const response = await fetch(`/api/profiles/search?query=${encodeURIComponent(query)}`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            const profiles = await response.json();
            renderUsers(profiles);
        } catch (error) {
            console.error('Ошибка поиска:', error);
        } finally {
            loading.style.display = 'none';
        }
    }

    function renderUsers(profiles) {
        if (profiles.length === 0) {
            usersGrid.innerHTML = '<div class="empty-state"><i class="fas fa-user-slash"></i><p>Ничего не найдено</p></div>';
            return;
        }

        usersGrid.innerHTML = profiles.map(profile => `
            <div class="user-card" onclick="window.location.href='/profile/${profile.userId}'">
                <img src="${profile.avatarUrl || '/images/default-avatar.png'}" alt="${profile.name}">
                <h3>${profile.name} ${profile.surname}</h3>
                <p>${profile.bioDescription || ''}</p>
                <button onclick="event.stopPropagation(); followUser('${profile.userId}')">
                    Подписаться
                </button>
            </div>
        `).join('');
    }

    window.followUser = function(userId) {
        console.log('Follow user:', userId);
    };
});