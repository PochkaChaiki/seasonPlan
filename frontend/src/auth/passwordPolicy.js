export const PASSWORD_HELP = 'Пароль: не менее 12 символов, не более 72 байт UTF-8.';
export const PASSWORD_VALIDATION_MESSAGE = 'Пароль должен содержать не менее 12 символов и не более 72 байт UTF-8.';

export function validateNewPassword(password) {
  if (!password) return 'Введите пароль.';
  return password.length >= 12 && new TextEncoder().encode(password).length <= 72
    ? '' : PASSWORD_VALIDATION_MESSAGE;
}
