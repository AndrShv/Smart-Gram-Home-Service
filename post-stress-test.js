import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.1.0/index.js';

// ─── CONFIG ───────────────────────────────────────────────
const AUTH_URL = 'http://localhost:8081';
const HOME_URL = 'http://localhost:8083'; // поменяй на свой порт

export let options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 0  },
    ],
    thresholds: {
        http_req_duration:                    ['p(95)<2000'],
        'http_req_duration{op:getFeed}':      ['p(95)<500'],
        'http_req_duration{op:getPost}':      ['p(95)<300'],
        'http_req_duration{op:reactToPost}':  ['p(95)<500'],
        'http_req_duration{op:searchPosts}':  ['p(95)<500'],
        http_req_failed:                      ['rate<0.05'],
    },
};

// ─── SETUP: создаём пользователя и пост для чтения ────────
export function setup() {
    const email    = `setup_${randomString(8)}@example.com`;
    const password = 'password123';

    http.post(`${AUTH_URL}/api/auth/register`,
        JSON.stringify({ username: `setup_${randomString(6)}`, email, password, role: 'USER' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const loginRes = http.post(`${AUTH_URL}/api/auth/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const token  = loginRes.json('token');
    const userId = loginRes.json('id');

    // создаём тестовый пост через multipart
    const photo = http.file(
        new Uint8Array([0xFF,0xD8,0xFF,0xE0,0x00,0x10,0x4A,0x46,0x49,0x46]).buffer,
        'test.jpg',
        'image/jpeg'
    );
    const createRes = http.post(`${HOME_URL}/api/posts`, {
        photo,
        description: 'setup post for stress test',
        isPublic: 'true',
    }, { headers: { 'Authorization': `Bearer ${token}` } });

    const postId = createRes.json('id');
    return { token, userId, postId };
}

// ─── MAIN ─────────────────────────────────────────────────
export default function (data) {
    const randomUser = randomString(6);
    const email      = `post_${randomUser}@example.com`;
    const password   = 'password123';

    // регистрация + логин каждого VU
    http.post(`${AUTH_URL}/api/auth/register`,
        JSON.stringify({ username: `post_${randomUser}`, email, password, role: 'USER' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const loginRes = http.post(`${AUTH_URL}/api/auth/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(loginRes, { 'login ok': (r) => r.status === 200 });
    if (loginRes.status !== 200) return;

    const token  = loginRes.json('token');
    const userId = loginRes.json('id');
    const headers = {
        'Authorization': `Bearer ${token}`,
    };

    sleep(0.5);

    // ── 1. Лента постов ─────────────────────────────────────
    const feedRes = http.get(`${HOME_URL}/api/posts/feed`,
        { headers, tags: { op: 'getFeed' } }
    );
    check(feedRes, { 'feed 200': (r) => r.status === 200 });

    sleep(0.5);

    // ── 2. Получить конкретный пост (setup post) ────────────
    if (data.postId) {
        const getRes = http.get(`${HOME_URL}/api/posts/${data.postId}`,
            { headers, tags: { op: 'getPost' } }
        );
        check(getRes, { 'get post 200': (r) => r.status === 200 });
        sleep(0.5);
    }

    // ── 3. Создать пост (multipart) ─────────────────────────
    const photo = http.file(
        new Uint8Array([0xFF,0xD8,0xFF,0xE0,0x00,0x10,0x4A,0x46,0x49,0x46]).buffer,
        'photo.jpg',
        'image/jpeg'
    );
    const createRes = http.post(`${HOME_URL}/api/posts`, {
        photo,
        description: `post by ${randomUser}`,
        location:    'Moscow',
        isPublic:    'true',
    }, { headers: { 'Authorization': `Bearer ${token}` }, tags: { op: 'createPost' } });

    check(createRes, { 'create post 201': (r) => r.status === 201 });
    const postId = createRes.json('id');

    sleep(0.5);

    // ── 4. Реакция на setup пост ────────────────────────────
    if (data.postId) {
        const reactions = ['LIKE', 'LOVE', 'HAHA', 'WOW', 'SAD', 'ANGRY'];
        const reaction  = reactions[Math.floor(Math.random() * reactions.length)];

        const reactRes = http.post(
            `${HOME_URL}/api/posts/${data.postId}/reaction`,
            JSON.stringify({ reaction }),
            { headers: { ...headers, 'Content-Type': 'application/json' }, tags: { op: 'reactToPost' } }
        );
        check(reactRes, { 'react 200': (r) => r.status === 200 });
        sleep(0.5);

        // ── 5. Счётчик реакций ──────────────────────────────
        const countRes = http.get(
            `${HOME_URL}/api/posts/${data.postId}/reactions/count`,
            { headers, tags: { op: 'getPost' } }
        );
        check(countRes, { 'reactions count 200': (r) => r.status === 200 });
        sleep(0.5);

        // ── 6. Удалить реакцию ──────────────────────────────
        const delReactRes = http.del(
            `${HOME_URL}/api/posts/${data.postId}/reaction`,
            null,
            { headers, tags: { op: 'reactToPost' } }
        );
        check(delReactRes, { 'delete reaction 204': (r) => r.status === 204 });
        sleep(0.5);
    }

    // ── 7. Посты пользователя ───────────────────────────────
    if (userId) {
        const userPostsRes = http.get(
            `${HOME_URL}/api/posts/user/${userId}`,
            { headers, tags: { op: 'getPost' } }
        );
        check(userPostsRes, { 'user posts 200': (r) => r.status === 200 });
        sleep(0.5);
    }

    // ── 8. Поиск постов ─────────────────────────────────────
    const searchRes = http.get(
        `${HOME_URL}/api/posts/search?query=post`,
        { headers, tags: { op: 'searchPosts' } }
    );
    check(searchRes, { 'search posts 200': (r) => r.status === 200 });

    sleep(0.5);

    // ── 9. Удалить свой пост ────────────────────────────────
    if (postId) {
        const delRes = http.del(
            `${HOME_URL}/api/posts/${postId}`,
            null,
            { headers, tags: { op: 'deletePost' } }
        );
        check(delRes, { 'delete post 204': (r) => r.status === 204 });
    }

    sleep(1);
}