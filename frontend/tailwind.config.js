/** @type {import('tailwindcss').Config} */
export default {
	content: ['./src/**/*.{html,js,svelte,ts}'],
	theme: {
		extend: {
			colors: {
				primary: '#000000',
				secondary: '#ffffff',
				accent: '#333333',
				border: '#e5e5e5'
			}
		}
	},
	plugins: []
};
