(() => {
  'use strict';
  const modal = document.getElementById('modal');
  const card = document.getElementById('modal-card');
  if (!modal || !card) return;
  const background = Array.from(document.querySelectorAll('#safa-app > .app-sidebar, #safa-app > .app-workspace'));
  const selector = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';
  let returnFocus = null;
  let wasOpen = false;
  const targets = () => Array.from(card.querySelectorAll(selector)).filter(el => !el.hidden && el.getAttribute('aria-hidden') !== 'true');
  const label = () => {
    const heading = card.querySelector('h1,h2,h3');
    if (!heading) return;
    if (!heading.id) heading.id = 'modal-title';
    modal.setAttribute('aria-labelledby', heading.id);
  };
  document.addEventListener('click', event => {
    const trigger = event.target.closest('[data-open-create],[data-open-wallet-ledger],[data-open-expense]');
    if (trigger) returnFocus = trigger;
  }, true);
  new MutationObserver(() => {
    const open = !modal.classList.contains('hidden');
    if (open) label();
    if (open && !wasOpen) {
      background.forEach(el => { el.inert = true; });
      (targets()[0] || card).focus({preventScroll: true});
    }
    if (!open && wasOpen) {
      background.forEach(el => { el.inert = false; });
      if (returnFocus && document.contains(returnFocus)) returnFocus.focus({preventScroll: true});
      returnFocus = null;
    }
    wasOpen = open;
  }).observe(modal, {attributes: true, attributeFilter: ['class'], childList: true, subtree: true});
  modal.addEventListener('keydown', event => {
    if (modal.classList.contains('hidden')) return;
    if (event.key === 'Escape') {
      const close = card.querySelector('[data-close-modal]');
      if (close) { event.preventDefault(); close.click(); }
      return;
    }
    if (event.key !== 'Tab') return;
    const list = targets();
    if (!list.length) { event.preventDefault(); card.focus(); return; }
    const first = list[0], last = list[list.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  card.tabIndex = -1;
})();
