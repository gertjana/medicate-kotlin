import { derived, writable } from 'svelte/store';
import { addMessages, init, locale, _ } from 'svelte-i18n';
import en from './locales/en.json';
import nl from './locales/nl.json';

// Add translations
addMessages('en', en);
addMessages('nl', nl);

// Initialize i18n
init({
  fallbackLocale: 'en',
  initialLocale: getInitialLocale(),
});

// Get initial locale from localStorage or browser
function getInitialLocale(): string {
  if (typeof window !== 'undefined') {
    const stored = localStorage.getItem('locale');
    if (stored && (stored === 'en' || stored === 'nl')) {
      return stored;
    }
    // Try to detect from browser language
    const browserLang = navigator.language.split('-')[0];
    if (browserLang === 'nl') {
      return 'nl';
    }
  }
  return 'en';
}

// Create a custom locale store that persists to localStorage
export const currentLocale = derived(locale, ($locale) => $locale);

export function setLocale(newLocale: string) {
  locale.set(newLocale);
  if (typeof window !== 'undefined') {
    localStorage.setItem('locale', newLocale);
  }
}

export { _ };
