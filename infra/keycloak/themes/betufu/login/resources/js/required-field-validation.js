(() => {
  'use strict';

  const installRequiredPasswordValidation = () => {
    const form = document.getElementById('kc-form-login');
    const password = document.getElementById('password');
    if (!(form instanceof HTMLFormElement) || !(password instanceof HTMLInputElement)) {
      return;
    }

    const errorId = 'betufu-password-required-error';
    const error = document.createElement('span');
    error.id = errorId;
    error.className = 'betufu-field-error';
    error.hidden = true;
    error.setAttribute('role', 'alert');
    password.insertAdjacentElement('afterend', error);

    const describedBy = new Set(
      (password.getAttribute('aria-describedby') ?? '')
        .split(/\s+/)
        .filter((id) => id.length > 0),
    );
    describedBy.add(errorId);
    password.setAttribute('aria-describedby', [...describedBy].join(' '));
    password.setAttribute('aria-errormessage', errorId);
    form.noValidate = false;
    password.required = true;

    let validationAttempted = false;
    const synchronizeError = () => {
      const invalid = validationAttempted && password.validity.valueMissing;
      password.setAttribute('aria-invalid', String(invalid));
      error.hidden = !invalid;
      error.textContent = invalid ? password.validationMessage : '';
    };

    password.addEventListener('invalid', (event) => {
      event.preventDefault();
      validationAttempted = true;
      synchronizeError();
      password.focus();
    });
    password.addEventListener('input', synchronizeError);
    synchronizeError();
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', installRequiredPasswordValidation, {
      once: true,
    });
  } else {
    installRequiredPasswordValidation();
  }
})();
