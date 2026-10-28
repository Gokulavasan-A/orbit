import * as Yup from 'yup';

/**
 * Client-side copy of the API's default password policy (app.security.password-policy and
 * PasswordPolicyServiceImpl). The API stays the authority: its fieldErrors on `newPassword`
 * are shown as well when the server is configured differently.
 */
export const passwordPolicy = {
  minLength: 12,
  maxLength: 64,
  /** BCrypt only uses the first 72 bytes. */
  maxUtf8Bytes: 72,
} as const;

export const passwordPolicyHints: string[] = [
  `${passwordPolicy.minLength} to ${passwordPolicy.maxLength} characters`,
  'At least one uppercase letter, one lowercase letter, one digit and one special character',
  'No spaces',
  'Not the same as your username or your current password',
];

const utf8 = new TextEncoder();

/** Code points, like Java's codePointCount (emoji count as one character). */
function length(value: string): number {
  return Array.from(value).length;
}

const hasUpper = (v: string) => /\p{Lu}/u.test(v);
const hasLower = (v: string) => /\p{Ll}/u.test(v);
const hasDigit = (v: string) => /\p{Nd}/u.test(v);
/** Not a letter, digit, whitespace or control character (same as the API). */
const hasSpecial = (v: string) => /[^\p{L}\p{Nd}\s\p{Cc}]/u.test(v);
const hasWhitespace = (v: string) => /\s/u.test(v);

/** Yup schema for the change-password form. `username` enables the not-equal-to-username rule. */
export function changePasswordSchema(username: string | undefined) {
  return Yup.object({
    currentPassword: Yup.string().required('Enter your current password.'),
    newPassword: Yup.string()
      .required('Enter a new password.')
      .test(
        'min',
        `Must be at least ${passwordPolicy.minLength} characters.`,
        (v) => length(v) >= passwordPolicy.minLength,
      )
      .test(
        'max',
        `Must be at most ${passwordPolicy.maxLength} characters.`,
        (v) => length(v) <= passwordPolicy.maxLength && utf8.encode(v).length <= passwordPolicy.maxUtf8Bytes,
      )
      .test('upper', 'Must contain an uppercase letter.', (v) => hasUpper(v))
      .test('lower', 'Must contain a lowercase letter.', (v) => hasLower(v))
      .test('digit', 'Must contain a digit.', (v) => hasDigit(v))
      .test('special', 'Must contain a special character.', (v) => hasSpecial(v))
      .test('spaces', 'Must not contain spaces.', (v) => !hasWhitespace(v))
      .test(
        'username',
        'Must not be the same as your username.',
        (v) => !username || v.toLowerCase() !== username.trim().toLowerCase(),
      )
      .test(
        'current',
        'Must be different from your current password.',
        (v, ctx) => !v || v !== (ctx.parent as { currentPassword?: string }).currentPassword,
      ),
    confirmPassword: Yup.string()
      .required('Confirm the new password.')
      .oneOf([Yup.ref('newPassword')], 'The passwords do not match.'),
  });
}
