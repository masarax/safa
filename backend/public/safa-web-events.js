(() => {
  'use strict';

  // The Android-style customer flow uses the secondary action as Back on a
  // two-step sale, but as Cancel on standalone due/advance adjustments. The
  // main workflow listener treats data-flow-back as navigation, so normalize
  // the standalone adjustment button before the bubbling listener sees it.
  document.addEventListener('click', event => {
    const button = event.target.closest('button[data-flow-back]');
    if (!button) return;

    const adjustmentForm = button.closest('#customer-flow-step2');
    if (!adjustmentForm?.querySelector('input[name="adjustment_amount"]')) return;

    button.removeAttribute('data-flow-back');
    button.setAttribute('data-flow-cancel', '');
  }, true);

  // The application runtime intentionally handles normal workspace forms with
  // fetch(), but the high-impact database updater must remain a regular Laravel
  // POST so CSRF, redirects and flash status are handled by the server. Stop the
  // bubbling runtime listener without cancelling the browser's native submit.
  document.addEventListener('submit', event => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (!form.matches('form[action$="/system/update/run"]')) return;

    event.stopImmediatePropagation();
  }, true);
})();
