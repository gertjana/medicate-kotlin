import { register, init, getLocaleFromNavigator, locale } from 'svelte-i18n';

register('en', () => import('./locales/en.json'));
register('nl', () => import('./locales/nl.json'));

// Get locale from cookie or browser
function getInitialLocale(): string {
  if (typeof document !== 'undefined') {
    const cookieLocale = document.cookie
      .split('; ')
      .find(row => row.startsWith('locale='))
      ?.split('=')[1];

    if (cookieLocale && ['en', 'nl'].includes(cookieLocale)) {
      return cookieLocale;
    }
  }

  return getLocaleFromNavigator() || 'en';
}

// Save locale to cookie when it changes
export function setLocale(newLocale: string) {
  locale.set(newLocale);
  if (typeof document !== 'undefined') {
    document.cookie = `locale=${newLocale}; path=/; max-age=31536000; SameSite=Strict`;
  }
}

const initialLocale = getInitialLocale();

init({
  fallbackLocale: 'en',
  initialLocale: initialLocale,
});

// Set the locale immediately to prevent rendering errors
locale.set(initialLocale);
