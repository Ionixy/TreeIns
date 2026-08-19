// Bump this whenever ASSETS changes, otherwise everyone who already installed the app
// keeps the previous cache and never sees the new files.
const CACHE_NAME = 'goaltree-v7';
const ASSETS = [
  './',              // the Pages root, which index.html answers
  './index.html',
  './TreeIns.html',
  './install.html',
  './ai-prompt.md',  // source of the AI prompt; also shareable as a plain link
  './ai-prompt.en.md', // the same, for the English UI
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './apple-touch-icon.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((names) =>
      Promise.all(names.filter((n) => n !== CACHE_NAME).map((n) => caches.delete(n)))
    ).then(() => self.clients.claim())
  );
});

// Network-first: an update to the app shows up on next load whenever there's a
// connection; the cache only kicks in once the network is actually unreachable.
self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  event.respondWith(
    fetch(event.request).then((res) => {
      const copy = res.clone();
      caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
      return res;
    }).catch(() => caches.match(event.request))
  );
});
