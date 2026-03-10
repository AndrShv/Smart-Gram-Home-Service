  const photoInput   = document.getElementById('photoInput');
  const uploadZone   = document.getElementById('uploadZone');
  const uploadPreview= document.getElementById('uploadPreview');
  const thumbImg     = document.getElementById('thumbImg');
  const previewBtn   = document.getElementById('previewBtn');
  const previewModal = document.getElementById('previewModal');
  const cancelPreview= document.getElementById('cancelPreview');
  const publishBtn   = document.getElementById('publishBtn');
  const successOverlay = document.getElementById('successOverlay');
  const toast        = document.getElementById('toast');

  let selectedFile = null;
  let objectUrl    = null;

  // ── DRAG & DROP ──
  uploadZone.addEventListener('dragover', e => { e.preventDefault(); uploadZone.classList.add('drag-over'); });
  uploadZone.addEventListener('dragleave', () => uploadZone.classList.remove('drag-over'));
  uploadZone.addEventListener('drop', e => {
      e.preventDefault(); uploadZone.classList.remove('drag-over');
      const file = e.dataTransfer.files[0];
      if (file && file.type.startsWith('image/')) handleFile(file);
  });

  photoInput.addEventListener('change', () => {
      if (photoInput.files[0]) handleFile(photoInput.files[0]);
  });

  function handleFile(file) {
      if (file.size > 10 * 1024 * 1024) { showToast('Файл слишком большой (макс. 10 МБ)'); return; }
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      objectUrl = URL.createObjectURL(file);
      selectedFile = file;
      thumbImg.src = objectUrl;
      uploadPreview.style.display = 'block';
      previewBtn.disabled = false;
  }

  // ── Live overlay text preview ──
  document.getElementById('overlayText').addEventListener('input', function() {
      const el = document.getElementById('previewOverlayText');
      el.textContent = this.value;
      el.style.display = this.value ? 'block' : 'none';
  });

  // ── OPEN PREVIEW ──
  previewBtn.addEventListener('click', openPreview);

  function openPreview() {
      // Image
      document.getElementById('previewImg').src = objectUrl;

      // Overlay text
      const ot = document.getElementById('overlayText').value;
      const otEl = document.getElementById('previewOverlayText');
      otEl.textContent = ot; otEl.style.display = ot ? 'block' : 'none';

      // Description
      const desc = document.getElementById('description').value;
      const descEl = document.getElementById('previewDesc');
      descEl.textContent = desc; descEl.style.display = desc ? 'block' : 'none';

      // Location
      const loc = document.getElementById('location').value;
      const locEl = document.getElementById('previewLocation');
      if (loc) { document.getElementById('previewLocationText').textContent = loc; locEl.style.display = 'inline-flex'; }
      else locEl.style.display = 'none';

      // Music
      const music = document.getElementById('musicCaption').value;
      const musicEl = document.getElementById('previewMusic');
      if (music) { document.getElementById('previewMusicText').textContent = music; musicEl.style.display = 'inline-flex'; }
      else musicEl.style.display = 'none';

      // AI tags (simulated client-side analysis)
      generateAiTags();

      // Reset progress animation by re-inserting element
      const fill = document.getElementById('progressFill');
      fill.style.animation = 'none';
      fill.offsetHeight; // reflow
      fill.style.animation = '';

      previewModal.classList.add('show');
  }

  cancelPreview.addEventListener('click', () => previewModal.classList.remove('show'));
  previewModal.addEventListener('click', e => { if (e.target === previewModal) previewModal.classList.remove('show'); });

  // ── SIMULATED AI TAGS ──
  function generateAiTags() {
      const desc = document.getElementById('description').value.toLowerCase();
      const loc  = document.getElementById('location').value.toLowerCase();
      const overlay = document.getElementById('overlayText').value.toLowerCase();
      const combined = desc + ' ' + loc + ' ' + overlay;

      const tagsMap = [
          { kw: ['море','пляж','ocean','sea','beach'], tag: 'море', type: '' },
          { kw: ['город','city','urban','улица'], tag: 'городской', type: '' },
          { kw: ['природа','лес','nature','forest','park'], tag: 'природа', type: '' },
          { kw: ['еда','food','ресторан','кафе'], tag: 'еда', type: '' },
          { kw: ['путешест','travel','поездк','trip'], tag: 'путешествие', type: '' },
          { kw: ['закат','рассвет','sunset','sunrise'], tag: 'закат', type: 'pink' },
          { kw: ['музык','music','концерт'], tag: 'музыка', type: '' },
          { kw: ['спорт','фитнес','sport','fitness'], tag: 'спорт', type: 'violet' },
      ];

      const found = tagsMap.filter(t => t.kw.some(k => combined.includes(k))).map(t => t);

      // Tags row in story
      const tagsRow = document.getElementById('previewTagsRow');
      tagsRow.innerHTML = '';
      found.slice(0, 4).forEach(t => {
          const span = document.createElement('span');
          span.className = 'story-tag';
          span.textContent = '#' + t.tag;
          tagsRow.appendChild(span);
      });

      // AI chips section
      const chips = document.getElementById('aiChips');
      chips.innerHTML = '';
      const aiSection = document.getElementById('aiTagsSection');

      const allChips = [...found];

      // Mood chip based on public toggle
      const isPublic = document.getElementById('isPublic').checked;
      allChips.push({ tag: isPublic ? '🌍 Публичная' : '🔒 Приватная', type: isPublic ? '' : 'violet' });

      // Time chip
      allChips.push({ tag: '⏳ 24 часа', type: 'pink' });

      if (allChips.length) {
          aiSection.style.display = 'block';
          allChips.forEach(t => {
              const chip = document.createElement('span');
              chip.className = 'ai-chip ' + (t.type || '');
              chip.textContent = t.tag;
              chips.appendChild(chip);
          });
      } else {
          aiSection.style.display = 'none';
      }
  }

  // ── PUBLISH ──
  publishBtn.addEventListener('click', async () => {
      if (!selectedFile) return;

      publishBtn.disabled = true;
      publishBtn.classList.add('loading');

      const formData = new FormData();
      formData.append('photo', selectedFile);
      formData.append('description', document.getElementById('description').value);
      formData.append('location',    document.getElementById('location').value);
      formData.append('overlayText', document.getElementById('overlayText').value);
      formData.append('musicCaption',document.getElementById('musicCaption').value);
      formData.append('isPublic',    document.getElementById('isPublic').checked);

      try {
          const res = await fetch('/api/stories', {
              method: 'POST',
              body: formData
              // Authorization через cookie (JWT) — Feign / браузер добавит автоматически
          });

          if (!res.ok) {
              const text = await res.text();
              throw new Error(text || 'Ошибка сервера');
          }

          // Успех
          previewModal.classList.remove('show');
          successOverlay.classList.add('show');
          setTimeout(() => { window.location.href = '/home'; }, 2200);

      } catch (err) {
          console.error(err);
          showToast('Ошибка: ' + err.message);
          publishBtn.disabled = false;
          publishBtn.classList.remove('loading');
      }
  });

  // ── TOAST ──
  function showToast(msg) {
      toast.textContent = msg;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 3500);
  }
