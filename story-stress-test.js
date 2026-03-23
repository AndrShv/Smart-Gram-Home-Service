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
        http_req_duration:                       ['p(95)<2000'],
        'http_req_duration{op:getStories}':      ['p(95)<500'],
        'http_req_duration{op:viewStory}':       ['p(95)<300'],
        'http_req_duration{op:reactToStory}':    ['p(95)<500'],
        http_req_failed:                         ['rate<0.05'],
    },
};

// ─── SETUP: создаём пользователя и сторис для чтения ──────
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

    // создаём тестовую сторис
    const photo = http.file(
        new Uint8Array([0xFF,0xD8,0xFF,0xE0,0x00,0x10,0x4A,0x46,0x49,0x46]).buffer,
        'test.jpg',
        'image/jpeg'
    );
    const createRes = http.post(`${HOME_URL}/api/stories`, {
        photo,
        description: 'setup story for stress test',
        isPublic:    'true',
    }, { headers: { 'Authorization': `Bearer ${token}` } });

    const storyId = createRes.json('id');
    return { token, userId, storyId };
}

// ─── MAIN ─────────────────────────────────────────────────
export default function (data) {
    const randomUser = randomString(6);
    const email      = `story_${randomUser}@example.com`;
    const password   = 'password123';

    // регистрация + логин
    http.post(`${AUTH_URL}/api/auth/register`,
        JSON.stringify({ username: `story_${randomUser}`, email, password, role: 'USER' }),
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
    const headers = { 'Authorization': `Bearer ${token}` };

    sleep(0.5);

    // ── 1. Получить все активные сторис ─────────────────────
    const allRes = http.get(`${HOME_URL}/api/stories`,
        { headers, tags: { op: 'getStories' } }
    );
    check(allRes, { 'get all stories 200': (r) => r.status === 200 });

    sleep(0.5);

    // ── 2. Создать свою сторис ──────────────────────────────
    const photo = http.file(
        new Uint8Array([0xFF,0xD8,0xFF,0xE0,0x00,0x10,0x4A,0x46,0x49,0x46]).buffer,
        'story.jpg',
        'image/jpeg'
    );
    const createRes = http.post(`${HOME_URL}/api/stories`, {
        photo,
        description: `story by ${randomUser}`,
        location:    'Moscow',
        isPublic:    'true',
    }, { headers, tags: { op: 'createStory' } });

    check(createRes, { 'create story 201': (r) => r.status === 201 });
    const myStoryId = createRes.json('id');

    sleep(0.5);

    // ── 3. Просмотр setup сторис ────────────────────────────
    if (data.storyId) {
        const viewRes = http.post(
            `${HOME_URL}/api/stories/${data.storyId}/view`,
            null,
            { headers, tags: { op: 'viewStory' } }
        );
        check(viewRes, { 'view story 200': (r) => r.status === 200 });
        sleep(0.5);

        // ── 4. Проверить просмотрен ли ──────────────────────
        const hasViewedRes = http.get(
            `${HOME_URL}/api/stories/${data.storyId}/views/me`,
            { headers, tags: { op: 'getStories' } }
        );
        check(hasViewedRes, {
            'has viewed 200': (r) => r.status === 200,
            'has viewed true': (r) => r.json() === true,
        });
        sleep(0.5);

        // ── 5. Счётчик просмотров ───────────────────────────
        const countViewsRes = http.get(
            `${HOME_URL}/api/stories/${data.storyId}/views/count`,
            { headers, tags: { op: 'getStories' } }
        );
        check(countViewsRes, { 'views count 200': (r) => r.status === 200 });
        sleep(0.5);

        // ── 6. Реакция на setup сторис ──────────────────────
        const reactions = ['LIKE', 'LOVE', 'HAHA', 'WOW', 'SAD', 'ANGRY'];
        const reaction  = reactions[Math.floor(Math.random() * reactions.length)];

        const reactRes = http.post(
            `${HOME_URL}/api/stories/${data.storyId}/reaction`,
            JSON.stringify({ reaction }),
            { headers: { ...headers, 'Content-Type': 'application/json' }, tags: { op: 'reactToStory' } }
        );
        check(reactRes, { 'react to story 200': (r) => r.status === 200 });
        sleep(0.5);

        // ── 7. Статистика реакций ───────────────────────────
        const statsRes = http.get(
            `${HOME_URL}/api/stories/${data.storyId}/reactions`,
            { headers, tags: { op: 'getStories' } }
        );
        check(statsRes, { 'reaction stats 200': (r) => r.status === 200 });
        sleep(0.5);

        // ── 8. Удалить свою реакцию ─────────────────────────
        const delReactRes = http.del(
            `${HOME_URL}/api/stories/${data.storyId}/reaction`,
            null,
            { headers, tags: { op: 'reactToStory' } }
        );
        check(delReactRes, { 'delete reaction 204': (r) => r.status === 204 });
        sleep(0.5);
    }

    // ── 9. Сторис конкретного пользователя ──────────────────
    if (data.userId) {
        const userStoriesRes = http.get(
            `${HOME_URL}/api/stories/user/${data.userId}`,
            { headers, tags: { op: 'getStories' } }
        );
        check(userStoriesRes, { 'user stories 200': (r) => r.status === 200 });
        sleep(0.5);
    }

    // ── 10. Удалить свою сторис ─────────────────────────────
    if (myStoryId) {
        const delRes = http.del(
            `${HOME_URL}/api/stories/${myStoryId}`,
            null,
            { headers, tags: { op: 'deleteStory' } }
        );
        check(delRes, { 'delete story 204': (r) => r.status === 204 });
    }

    sleep(1);
}