# Medicine Scheduler Frontend

A minimalistic black and white SvelteKit frontend for the Medicine Scheduler API.

## Features

- **Dashboard**: View today's medicine schedule grouped by time with quick "Take Dose" buttons
- **Medicines**: Full CRUD operations for medicines with stock management
- **Schedules**: Create and manage daily medication schedules
- **History**: Placeholder for dosage history (requires backend implementation)

## Setup

1. Install dependencies:
```bash
npm install
```

2. Start the development server:
```bash
npm run dev
```

The app will be available at `http://localhost:5173`

## Backend Connection

The frontend is configured to proxy API requests through Vite:
- Frontend requests to `/api/*` are forwarded to `http://localhost:8080`
- Make sure your Ktor backend is running on port 8080

## Build for Production

```bash
npm run build
```

The static files will be generated in the `build/` directory.

## Project Structure

```
src/
├── lib/
│   └── api.ts              # API client functions
├── routes/
│   ├── +layout.svelte      # Main layout with navigation
│   ├── +page.svelte        # Dashboard (daily schedule)
│   ├── medicines/
│   │   └── +page.svelte    # Medicine management
│   ├── schedules/
│   │   └── +page.svelte    # Schedule management
│   └── history/
│       └── +page.svelte    # Dosage history
├── app.css                 # Global styles with Tailwind
└── app.html                # HTML template
```

## Design

- Minimalistic black and white theme
- Responsive layout
- Clean, accessible UI with clear visual hierarchy
- Stock warnings when medicine levels are low
