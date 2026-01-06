<script lang="ts">
	import '../app.css';
	import { page } from '$app/stores';
	
	// Suppress warnings about SvelteKit props
	export let data = {};
	export let params = {};

	const navItems = [
		{ path: '/', label: 'Dashboard' },
		{ path: '/medicines', label: 'Medicines' },
		{ path: '/schedules', label: 'Schedules' },
		{ path: '/history', label: 'History' }
	];
</script>

<div class="min-h-screen flex flex-col">
	<header class="border-b border-black">
		<div class="pr-4 py-4">
			<div class="flex items-start gap-6">
				<img src="/medication.svg" alt="Medicine Scheduler Logo" class="h-24 w-24 flex-shrink-0 ml-4" style="filter: invert(48%) sepia(79%) saturate(2476%) hue-rotate(184deg) brightness(91%) contrast(87%);" />
				<div class="flex-1">
					<h1 class="text-2xl font-bold mb-4">Medicate</h1>
					<nav class="flex gap-2">
						{#each navItems as item}
							<a
								href={item.path}
								class="nav-link {$page.url.pathname === item.path ? 'nav-link-active' : ''}"
							>
								{item.label}
							</a>
						{/each}
					</nav>
				</div>
			</div>
		</div>
	</header>

	<main class="flex-1 container mx-auto px-4 py-8">
		<slot />
	</main>

	<footer class="border-t border-black">
		<div class="container mx-auto px-4 py-4 text-center text-sm text-gray-600">
			Medicate &copy; {new Date().getFullYear()}
		</div>
	</footer>
</div>
