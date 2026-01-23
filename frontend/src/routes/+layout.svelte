<script lang="ts">
	import '../app.css';
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import { userStore } from '$lib/stores/user';
	import { registerUser, loginUser, requestPasswordReset } from '$lib/api';

	// SvelteKit props - using const since they're not used internally
	export const data = {};
	export const params = {};

	const navItems = [
		{ path: '/', label: 'Dashboard' },
		{ path: '/medicines', label: 'Medicines' },
		{ path: '/schedules', label: 'Schedules' },
		{ path: '/history', label: 'History' }
	];

	let showAuthModal = false;
	let authMode: 'login' | 'register' = 'login';
	let username = '';
	let password = '';
	let email = '';
	let authError = '';
	let showProfile = false;
	let profileLeft = 0;
	let profileTop = 0;
	let profileUseFixed = false;
	let profileRight = 0;
	let showForgotPassword = false;
	let forgotPasswordUsername = '';
	let forgotPasswordError = '';
	let forgotPasswordSuccess = '';

	// Toast notification state - support multiple stacked toasts
	interface Toast {
		id: number;
		message: string;
		type: 'success' | 'error' | 'info';
	}
	let toasts: Toast[] = [];
	let toastIdCounter = 0;

	function showToastNotification(message: string, type: 'success' | 'error' | 'info' = 'info') {
		const id = toastIdCounter++;
		toasts = [...toasts, { id, message, type }];
		setTimeout(() => {
			toasts = toasts.filter(t => t.id !== id);
		}, 6000);
	}

	$: profileInlineStyle = profileUseFixed
		? `position:fixed; right:${profileRight}px; top:${profileTop}px; min-width:24rem; min-height:8rem; width:auto; max-width:calc(100vw - 2rem);`
		: `right:0; top:calc(100% + 0.5rem); min-width:24rem; min-height:8rem; width:auto; max-width:calc(100vw - 2rem);`;

	onMount(() => {
		userStore.init();
		// Close profile when clicking outside
		const onDocClick = () => { showProfile = false; };
		window.addEventListener('click', onDocClick);
		return () => window.removeEventListener('click', onDocClick);
	});

	async function handleAuth() {
		authError = '';
		if (!username.trim()) {
			authError = 'Username is required';
			return;
		}
		if (!password) {
			authError = 'Password is required';
			return;
		}
		if (authMode === 'register') {
			if (!email || !email.includes('@')) {
				authError = 'A valid email is required';
				return;
			}
			if (password.length < 6) {
				authError = 'Password must be at least 6 characters';
				return;
			}
		}

		try {
			if (authMode === 'register') {
				// Registration: show success message, do NOT log user in
				const registrationResponse = await registerUser(username.trim(), password, email.trim());

				// Close modal and show success notification
				showAuthModal = false;
				username = '';
				password = '';
				email = '';

				// Show success notification with the email address
				showToastNotification(
					`Registration successful! Please check your email (${registrationResponse.email}) to activate your account.`,
					'success'
				);
			} else {
				// Login: log the user in as before
				const user = await loginUser(username.trim(), password);
				userStore.login(user);
				showAuthModal = false;
				username = '';
				password = '';
				email = '';
			}
		} catch (e) {
			authError = e instanceof Error ? e.message : 'Authentication failed';
		}
	}

	async function handleLogout() {
		// Close profile popup to avoid flicker
		showProfile = false;
		// Perform logout then redirect to dashboard to avoid "Authentication required" pages
		await userStore.logout();
		// Navigate to dashboard (root) and replace history so back doesn't return to protected page
		goto('/', { replaceState: true });
	}

	async function handleChangePassword() {
		// Close profile popup
		showProfile = false;

		if (!$userStore?.email) {
			showToastNotification('No email address found for your account', 'error');
			return;
		}

		// Show immediate feedback that email is being sent
		showToastNotification('Sending password reset email...', 'info');

		try {
			await requestPasswordReset($userStore.email);
			showToastNotification('Password reset email sent! Check your inbox for instructions.', 'success');
		} catch (e) {
			showToastNotification(e instanceof Error ? e.message : 'Failed to send password reset email', 'error');
		}
	}

	function openAuthModal(mode: 'login' | 'register') {
		authMode = mode;
		username = '';
		password = '';
		authError = '';
		showAuthModal = true;
	}

	function openForgotPassword() {
		showAuthModal = false;
		showForgotPassword = true;
		forgotPasswordUsername = '';
		forgotPasswordError = '';
		forgotPasswordSuccess = '';
	}

	async function handleForgotPassword() {
		forgotPasswordError = '';
		forgotPasswordSuccess = '';

		if (!forgotPasswordUsername.trim()) {
			forgotPasswordError = 'Email address is required';
			return;
		}

		// Basic email validation
		if (!forgotPasswordUsername.includes('@')) {
			forgotPasswordError = 'Please enter a valid email address';
			return;
		}

		try {
			await requestPasswordReset(forgotPasswordUsername.trim());
			forgotPasswordSuccess = 'Password reset email sent! Please check your inbox.';
			forgotPasswordUsername = '';
		} catch (e) {
			forgotPasswordError = e instanceof Error ? e.message : 'Failed to send reset email';
		}
	}

	function toggleProfile(event: MouseEvent) {
		// prevent the global click listener from immediately closing the popup
		event.stopPropagation();

		// compute a fixed viewport position so we can clamp the popup inside the viewport
		// Align the popup's right edge with the wrapper's right edge by default
		// find the nearest .relative wrapper (the username+logout container) so we align under the whole group
		const button = event.currentTarget as HTMLElement | null;
		const wrapper = button?.closest('.relative') as HTMLElement | null;
		const anchor = wrapper ?? button;
		if (anchor && window?.innerWidth) {
			const rect = anchor.getBoundingClientRect();
			const popupWidth = Math.min(24 * 16, window.innerWidth - 32); // 24rem but leave margins
			const margin = 8;

			let left = rect.right - popupWidth;
			if (left + popupWidth + margin > window.innerWidth) {
				left = Math.max(margin, window.innerWidth - popupWidth - margin);
			}
			if (left < margin) left = margin;

			let top = rect.bottom + window.scrollY + 8;
			// if it would overflow bottom, try placing above the anchor
			const popupHeightEstimate = 160;
			if (top - window.scrollY + popupHeightEstimate + margin > window.innerHeight) {
				top = Math.max(window.scrollY + margin, rect.top + window.scrollY - popupHeightEstimate - 8);
			}

			profileLeft = Math.round(left);
			profileTop = Math.round(top);
			profileUseFixed = true;
			// compute right offset from viewport right edge so popup aligns with wrapper's right edge
			profileRight = Math.max(margin, Math.round(window.innerWidth - rect.right));
 		} else {
 			profileUseFixed = false;
 		}

 		showProfile = !showProfile;
 	}
</script>

<div class="min-h-screen flex flex-col">
	<header class="border-b border-black" style="margin-bottom: 2em;">
			<div class="pr-4 pt-4 pb-0">
					<div class="flex items-start gap-6">
						<img src="/medication.svg" alt="Medicine Scheduler Logo" class="h-12 w-12 flex-shrink-0 ml-4" style="filter: invert(48%) sepia(79%) saturate(2476%) hue-rotate(184deg) brightness(91%) contrast(87%);" />
						<div class="flex-1">
							<div class="flex justify-between items-center mb-0">
								<div class="flex items-center gap-6">
									<h1 class="text-2xl font-bold">Medicate</h1>
									<nav class="hidden md:flex items-center gap-2">
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
								<div class="flex items-center gap-2">
									{#if $userStore}
										<div class="relative flex items-center gap-2 overflow-visible">
											<button on:click={toggleProfile} class="text-sm font-semibold flex items-center gap-2" aria-expanded={showProfile} aria-haspopup="true">
												<span>{$userStore.username}</span>
											</button>
											{#if showProfile}
												<div
													on:click|stopPropagation
													on:keydown={(e) => e.key === 'Escape' && (showProfile = false)}
													role="menu"
													tabindex="-1"
													class="absolute bg-white border border-gray-200 shadow-lg rounded p-3 z-50 flex flex-col gap-2"
													style={profileInlineStyle}
												>
													{#if $userStore.firstName || $userStore.lastName}
														<p class="text-sm text-gray-700 font-semibold border-b border-gray-200 pb-2 mb-1">
															{$userStore.firstName || ''} {$userStore.lastName || ''}
														</p>
													{/if}
													{#if $userStore.email}
														<p class="text-xs text-gray-500 mb-2" style="max-width:100%; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">{$userStore.email}</p>
													{/if}
													<a href="/profile" on:click={() => showProfile = false} class="text-sm text-blue-600 hover:text-blue-800 hover:underline">
														Edit Profile
													</a>
													<button on:click={handleChangePassword} class="text-sm text-blue-600 hover:text-blue-800 hover:underline text-left">
														Change Password
													</button>
												</div>
											{/if}
											<button on:click={handleLogout} class="btn btn-nav text-xs">Logout</button>
										</div>
									{:else}
										<button on:click={() => openAuthModal('login')} class="btn btn-nav text-xs">Login</button>
										<button on:click={() => openAuthModal('register')} class="btn btn-nav text-xs">Register</button>
									{/if}
								</div>
							</div>
						</div>
					</div>
				</div>
	</header>

	<main class="flex-1 container mx-auto px-4 pt-0 pb-8">
		<slot />
	</main>

	<footer class="border-t border-black">
		<div class="container mx-auto px-4 py-4 text-center text-sm text-gray-600">
			<a href="https://gertjanassies.dev">gertjanassies.dev</a> &copy; {new Date().getFullYear()}
		</div>
	</footer>
</div>

<!-- Auth Modal -->
{#if showAuthModal}
	<div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
		<div class="bg-white border border-black p-6 max-w-md w-full rounded-tr-lg rounded-bl-lg">
			<h3 class="text-xl font-bold mb-4">
				{authMode === 'register' ? 'Register' : 'Login'}
			</h3>
			<form on:submit|preventDefault={handleAuth}>
				<div class="mb-4">
					<label for="auth-username" class="block mb-1 font-semibold">Username</label>
					<input
						id="auth-username"
						type="text"
						bind:value={username}
						class="input w-full"
						placeholder="Enter your username"
						required
					/>
				</div>
				<div class="mb-4">
					<label for="auth-password" class="block mb-1 font-semibold">Password</label>
					<input
						id="auth-password"
						type="password"
						bind:value={password}
						class="input w-full"
						placeholder="Enter your password"
						required
						minlength="6"
					/>
					{#if authMode === 'register'}
						<p class="text-xs text-gray-600 mt-1">Minimum 6 characters</p>
					{/if}
					{#if authMode === 'register'}
					<div class="mb-4 mt-2">
						<label for="auth-email" class="block mb-1 font-semibold">Email</label>
						<input id="auth-email" type="email" bind:value={email} class="input w-full" placeholder="Enter your email" required />
					</div>
					{/if}
				</div>
				{#if authError}
					<div class="mb-4 p-3 bg-red-50 border border-red-300 text-red-800 text-sm rounded">
						{authError}
					</div>
				{/if}
				<div class="flex gap-2">
					<button type="submit" class="btn btn-nav flex-1">
						{authMode === 'register' ? 'Register' : 'Login'}
					</button>
					<button type="button" on:click={() => showAuthModal = false} class="btn btn-nav flex-1">
						Cancel
					</button>
				</div>
				<div class="mt-4 text-center text-sm">
					{#if authMode === 'login'}
						<button
							type="button"
							on:click={() => { authMode = 'register'; authError = ''; password = ''; }}
							class="text-[steelblue] hover:underline"
						>
							Don't have an account? Register
						</button>
						<div class="mt-2">
							<button
								type="button"
								on:click={openForgotPassword}
								class="text-[steelblue] hover:underline text-xs"
							>
								Forgot password?
							</button>
						</div>
					{:else}
						<button
							type="button"
							on:click={() => { authMode = 'login'; authError = ''; password = ''; }}
							class="text-[steelblue] hover:underline"
						>
							Already have an account? Login
						</button>
					{/if}
				</div>
			</form>
		</div>
	</div>
{/if}

<!-- Forgot Password Modal -->
{#if showForgotPassword}
	<div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
		<div class="bg-white border border-black p-6 max-w-md w-full rounded-tr-lg rounded-bl-lg">
			<h3 class="text-xl font-bold mb-4">Reset Password</h3>
			<form on:submit|preventDefault={handleForgotPassword}>
				<div class="mb-4">
					<label for="forgot-email" class="block mb-1 font-semibold">Email Address</label>
					<input
						id="forgot-email"
						type="email"
						bind:value={forgotPasswordUsername}
						class="input w-full"
						placeholder="Enter your email address"
						required
					/>
					<p class="text-xs text-gray-600 mt-1">We'll send a password reset link to this email</p>
				</div>
				{#if forgotPasswordError}
					<div class="mb-4 p-3 bg-red-50 border border-red-300 text-red-800 text-sm rounded">
						{forgotPasswordError}
					</div>
				{/if}
				{#if forgotPasswordSuccess}
					<div class="mb-4 p-3 bg-green-50 border border-green-300 text-green-800 text-sm rounded">
						{forgotPasswordSuccess}
					</div>
				{/if}
				<div class="flex gap-2">
					<button type="submit" class="btn btn-nav flex-1">
						Send Reset Link
					</button>
					<button type="button" on:click={() => { showForgotPassword = false; showAuthModal = true; }} class="btn btn-nav flex-1">
						Back to Login
					</button>
				</div>
			</form>
		</div>
	</div>
{/if}

<!-- Toast Notifications - stacked -->
<div class="fixed top-[4.125rem] right-4 z-50 flex flex-col gap-2">
	{#each toasts as toast (toast.id)}
		<div class="animate-slide-up">
			<div class="p-4 rounded-lg shadow-lg border-2 {toast.type === 'success' ? 'bg-green-50 border-green-500 text-green-800' : toast.type === 'error' ? 'bg-red-50 border-red-500 text-red-800' : 'bg-blue-50 border-blue-500 text-blue-800'}">
				{toast.message}
			</div>
		</div>
	{/each}
</div>
