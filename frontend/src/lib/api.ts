export interface Medicine {
	id: string;
	name: string;
	dose: number;
	unit: string;
	stock: number;
	description?: string;
	bijsluiter?: string;
}

export interface MedicineSearchResult {
	productnaam: string;
	farmaceutischevorm: string;
	werkzamestoffen: string;
	bijsluiter_filenaam: string;
}

export interface User {
	username: string;
	email?: string;
	firstName?: string;
	lastName?: string;
	isAdmin?: boolean;
}

export interface AuthResponse {
	user: User;
	token: string;  // Access token (short-lived, 1 hour)
	refreshToken: string;  // Refresh token (long-lived, 30 days)
}

export interface Schedule {
	id: string;
	medicineId: string;
	time: string;
	amount: number;
	daysOfWeek?: string; // Comma-separated day codes like "MO,WE,FR"
}

export interface DosageHistory {
	id: string;
	datetime: string;
	medicineId: string;
	amount: number;
	scheduledTime?: string;
}

export interface MedicineScheduleItem {
	medicine: Medicine;
	amount: number;
}

export interface TimeSlot {
	time: string;
	medicines: MedicineScheduleItem[];
}

export interface DailySchedule {
	schedule: TimeSlot[];
}

export interface DayAdherence {
	date: string;
	dayOfWeek: string;
	dayNumber: number;
	month: number;
	status: 'NONE' | 'PARTIAL' | 'COMPLETE';
	expectedCount: number;
	takenCount: number;
}

export interface WeeklyAdherence {
	days: DayAdherence[];
}

export interface MedicineExpiry {
	id: string;
	name: string;
	dose: number;
	unit: string;
	stock: number;
	description?: string;
	expiryDate?: string;
}

// Determine API base URL based on environment
// - Server-side (SSR): Use internal backend URL (http://127.0.0.1:8080/api)
// - Client-side: Use relative URL (/api) which nginx proxies to backend
import { browser } from '$app/environment';

const API_BASE = browser
	? '/api'  // Client-side: relative URL, proxied by nginx
	: 'http://127.0.0.1:8080/api';  // Server-side: direct internal connection

// Store access token in memory (not localStorage for security)
let accessToken: string | null = null;

// Singleton in-flight refresh promise — prevents parallel refresh requests
// when multiple API calls race on a cold page load.
let refreshPromise: Promise<boolean> | null = null;

// Helper function to get the current access token
export function getAccessToken(): string | null {
	return accessToken;
}

// Helper function to set the access token
export function setAccessToken(token: string | null): void {
	accessToken = token;
}

// Parse isAdmin from a JWT access token without verifying the signature.
// Server-side verification already happened; this is only for UI display.
export function parseIsAdminFromToken(token: string): boolean {
	try {
		const payloadB64 = token.split('.')[1];
		if (!payloadB64) return false;
		// Replace URL-safe base64 chars and pad
		const padded = payloadB64.replace(/-/g, '+').replace(/_/g, '/');
		const json = atob(padded);
		const payload = JSON.parse(json);
		return payload.isAdmin === true;
	} catch {
		return false;
	}
}

// Helper function to get headers with JWT token
function getHeaders(includeContentType: boolean = false, locale?: string): HeadersInit {
	const headers: HeadersInit = {};

	// Get JWT token from memory (not localStorage)
	if (browser && accessToken) {
		headers['Authorization'] = `Bearer ${accessToken}`;
	}

	if (includeContentType) {
		headers['Content-Type'] = 'application/json';
	}

	// Add locale header if provided
	if (locale) {
		headers['Accept-Language'] = locale;
	}

	return headers;
}

// Helper function to refresh the access token using refresh token from cookie
async function refreshAccessToken(): Promise<boolean> {
	if (!browser) return false;

	// Deduplicate: if a refresh is already in flight, wait for it instead of
	// firing another request (prevents N parallel refreshes on cold page load).
	if (refreshPromise) return refreshPromise;

	refreshPromise = (async () => {
		try {
			const response = await fetch(`${API_BASE}/auth/refresh`, {
				method: 'POST',
				credentials: 'include' // Include cookies
			});

			if (!response.ok) {
				return false;
			}

			const data = await response.json();
			setAccessToken(data.token);
			return true;
		} catch (e) {
			console.error('Failed to refresh token:', e);
			return false;
		} finally {
			refreshPromise = null;
		}
	})();

	return refreshPromise;
}

// Helper function to handle API responses and auto-refresh/logout on 401
async function handleApiResponse(response: Response, retryFn?: () => Promise<Response>): Promise<any> {
	if (response.status === 401) {
		// Try to refresh the token
		const refreshed = await refreshAccessToken();

		if (refreshed && retryFn) {
			// Retry the original request with new token
			const retryResponse = await retryFn();
			if (retryResponse.ok || retryResponse.status !== 401) {
				// Retry succeeded or failed for different reason
				return handleApiResponse(retryResponse); // Recursive call without retry to avoid infinite loop
			}
		}

		// Token refresh failed or retry failed - logout user
		if (browser) {
			setAccessToken(null);
			localStorage.removeItem('medicate_user');
			// Navigate to root to show login page (reload avoided to prevent looping)
			window.location.href = '/';
		}
		throw new Error('Session expired. Please login again.');
	}

	if (!response.ok) {
		// Try to get error message from response
		let errorData;
		try {
			errorData = await response.json();
		} catch (jsonError) {
			// JSON parsing failed (empty or malformed response)
			// Fall back to status-based error message
			throw new Error(`Request failed with status ${response.status}`);
		}
		// Successfully parsed error response
		throw new Error(errorData.error || `Request failed with status ${response.status}`);
	}

	// Return empty object for 204 No Content responses
	if (response.status === 204) {
		return {};
	}

	return response.json();
}

// Helper to make authenticated fetch requests with automatic retry on token refresh
async function authenticatedFetch(url: string, options: RequestInit = {}): Promise<any> {
	const makeRequest = () => {
		// Merge headers: spread user headers first, then add auth headers (without Content-Type if already provided)
		const headers = { ...options.headers };
		const hasContentType = 'Content-Type' in headers;

		// Add Authorization header (and Content-Type if not already present and body exists)
		const authHeaders = getHeaders(!hasContentType && options.body != null);
		Object.assign(headers, authHeaders);

		return fetch(url, {
			...options,
			headers,
			credentials: 'include' // Include cookies for refresh token
		});
	};

	const response = await makeRequest();
	return handleApiResponse(response, makeRequest);
}

// Medicine API
export async function getMedicines(): Promise<Medicine[]> {
	return authenticatedFetch(`${API_BASE}/medicine`, {
		cache: 'no-store'
	});
}

export async function searchMedicines(query: string): Promise<MedicineSearchResult[]> {
	if (query.length < 2) {
		return [];
	}
	return authenticatedFetch(`${API_BASE}/medicines/search?q=${encodeURIComponent(query)}`, {
		cache: 'no-store'
	});
}

export async function getMedicine(id: string): Promise<Medicine> {
	return authenticatedFetch(`${API_BASE}/medicine/${id}`, {
		cache: 'no-store'
	});
}

export async function createMedicine(medicine: Omit<Medicine, 'id'>): Promise<Medicine> {
	return authenticatedFetch(`${API_BASE}/medicine`, {
		method: 'POST',
		body: JSON.stringify(medicine)
	});
}

export async function updateMedicine(id: string, medicine: Medicine): Promise<Medicine> {
	return authenticatedFetch(`${API_BASE}/medicine/${id}`, {
		method: 'PUT',
		body: JSON.stringify(medicine)
	});
}

export async function deleteMedicine(id: string): Promise<void> {
	return authenticatedFetch(`${API_BASE}/medicine/${id}`, {
		method: 'DELETE'
	});
}

export async function addStock(medicineId: string, amount: number): Promise<Medicine> {
	return authenticatedFetch(`${API_BASE}/addstock`, {
		method: 'POST',
		body: JSON.stringify({ medicineId, amount })
	});
}

// Schedule API
export async function getSchedules(): Promise<Schedule[]> {
	return authenticatedFetch(`${API_BASE}/schedule`, {
		cache: 'no-store'
	});
}

export async function getSchedule(id: string): Promise<Schedule> {
	return authenticatedFetch(`${API_BASE}/schedule/${id}`, {
		cache: 'no-store'
	});
}

export async function createSchedule(schedule: Omit<Schedule, 'id'>): Promise<Schedule> {
	return authenticatedFetch(`${API_BASE}/schedule`, {
		method: 'POST',
		body: JSON.stringify(schedule)
	});
}

export async function updateSchedule(id: string, schedule: Schedule): Promise<Schedule> {
	return authenticatedFetch(`${API_BASE}/schedule/${id}`, {
		method: 'PUT',
		body: JSON.stringify(schedule)
	});
}

export async function deleteSchedule(id: string): Promise<void> {
	return authenticatedFetch(`${API_BASE}/schedule/${id}`, {
		method: 'DELETE'
	});
}

// Daily schedule
export async function getDailySchedule(): Promise<DailySchedule> {
	return authenticatedFetch(`${API_BASE}/daily`, {
		cache: 'no-store'
	});
}

// Dosage history
export async function takeDose(medicineId: string, amount: number, scheduledTime?: string, datetime?: string): Promise<DosageHistory> {
	return authenticatedFetch(`${API_BASE}/takedose`, {
		method: 'POST',
		body: JSON.stringify({ medicineId, amount, scheduledTime, datetime })
	});
}

export async function getDosageHistories(): Promise<DosageHistory[]> {
	return authenticatedFetch(`${API_BASE}/history`, {
		cache: 'no-store'
	});
}

export async function deleteDosageHistory(id: string): Promise<void> {
	return authenticatedFetch(`${API_BASE}/history/${id}`, {
		method: 'DELETE'
	});
}


// Adherence and analytics
export async function getWeeklyAdherence(): Promise<WeeklyAdherence> {
	return authenticatedFetch(`${API_BASE}/adherence`, {
		cache: 'no-store'
	});
}


// Registration response (when account activation is required)
export interface RegistrationResponse {
	message: string;
	email: string;
}

// User authentication API
export async function registerUser(username: string, password: string, email: string, locale: string = 'en'): Promise<RegistrationResponse> {
    const body: any = { username, password, email };

    const url = `${API_BASE}/user/register`;

    const headers: HeadersInit = {
        'Content-Type': 'application/json',
        'Accept-Language': locale
    };

    const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(body)
    });

    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.error || `Failed to register user: ${response.status} ${response.statusText}`);
    }

    const registrationResponse: RegistrationResponse = await response.json();

    // User account is created but NOT logged in (account needs activation via email)
    return registrationResponse;
}

export async function loginUser(username: string, password: string): Promise<User> {
	const response = await fetch(`${API_BASE}/user/login`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ username, password }),
		credentials: 'include' // Include cookies
	});
	if (!response.ok) throw new Error('Failed to login');

	const authResponse: AuthResponse = await response.json();

	// Store user in localStorage and access token in memory
	// Refresh token is in HttpOnly cookie (set by server)
	// isAdmin is intentionally NOT persisted to localStorage — read from JWT claims instead
	if (browser) {
		const { isAdmin: _stripped, ...userWithoutAdmin } = authResponse.user;
		localStorage.setItem('medicate_user', JSON.stringify(userWithoutAdmin));
		setAccessToken(authResponse.token);
	}

	// Enrich user object with isAdmin from JWT (not from server response body, not from localStorage)
	const isAdmin = parseIsAdminFromToken(authResponse.token);
	return { ...authResponse.user, isAdmin };
}

export async function requestPasswordReset(email: string, locale: string = 'en'): Promise<{ message: string; emailId: string }> {
	const headers: HeadersInit = {
		'Content-Type': 'application/json',
		'Accept-Language': locale
	};

	const response = await fetch(`${API_BASE}/auth/resetPassword`, {
		method: 'POST',
		headers,
		body: JSON.stringify({ email })
	});
	if (!response.ok) {
		const error = await response.json();
		throw new Error(error.error || 'Failed to request password reset');
	}
	return response.json();
}

export async function updatePassword(token: string, newPassword: string): Promise<void> {
	const response = await fetch(`${API_BASE}/auth/updatePassword`, {
		method: 'PUT',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ token, password: newPassword })
	});
	if (!response.ok) {
		const error = await response.json();
		throw new Error(error.error || 'Failed to update password');
	}
}

export async function getMedicineExpiry(): Promise<MedicineExpiry[]> {
	return authenticatedFetch(`${API_BASE}/medicineExpiry`, {
		cache: 'no-store'
	});
}

// Logout function to clear authentication
export async function logout(): Promise<void> {
	if (browser) {
		// Call backend to clear HttpOnly cookie
		try {
			await fetch(`${API_BASE}/auth/logout`, {
				method: 'POST',
				credentials: 'include'
			});
		} catch (e) {
			console.error('Failed to logout on server:', e);
		}

		// Clear user from localStorage and access token from memory
		localStorage.removeItem('medicate_user');
		setAccessToken(null);
	}
}

// Get user profile
export async function getProfile(): Promise<User> {
	return authenticatedFetch(`${API_BASE}/user/profile`, {
		method: 'GET'
	});
}

// Update user profile
export async function updateProfile(email: string, firstName: string, lastName: string): Promise<User> {
	const user = await authenticatedFetch(`${API_BASE}/user/profile`, {
		method: 'PUT',
		body: JSON.stringify({ email, firstName, lastName })
	});

	// Update stored user in localStorage — strip isAdmin so it's never persisted
	if (browser) {
		const { isAdmin: _stripped, ...userWithoutAdmin } = user;
		localStorage.setItem('medicate_user', JSON.stringify(userWithoutAdmin));
	}

	// Re-attach isAdmin from current in-memory token
	const isAdmin = accessToken ? parseIsAdminFromToken(accessToken) : false;
	return { ...user, isAdmin };
}

// Request password change (sends email with reset link)
export async function requestPasswordChange(email: string, locale: string = 'en'): Promise<{ message: string; emailId: string }> {
	const headers: HeadersInit = {
		'Content-Type': 'application/json',
		'Accept-Language': locale
	};

	const response = await fetch(`${API_BASE}/auth/resetPassword`, {
		method: 'POST',
		headers,
		credentials: 'include',
		body: JSON.stringify({ email })
	});
	if (!response.ok) {
		const error = await response.json();
		throw new Error(error.error || 'Failed to request password change');
	}
	return response.json();
}

// Helper to check if user is logged in
export function isLoggedIn(): boolean {
	if (!browser) return false;
	return accessToken !== null || localStorage.getItem('medicate_user') !== null;
}

// Helper to get current user
export function getCurrentUser(): User | null {
	if (!browser) return null;
	const userJson = localStorage.getItem('medicate_user');
	if (!userJson) return null;
	try {
		return JSON.parse(userJson);
	} catch (e) {
		console.error('Failed to parse user from localStorage', e);
		return null;
	}
}

export interface AdminUser {
	id: string;
	username: string;
	email: string;
	firstName: string;
	lastName: string;
	isActive: boolean;
	isAdmin: boolean;
	isSelf: boolean;
}

export interface AdminUsersListResponse {
	users: AdminUser[];
}

export async function getAllUsers(): Promise<AdminUsersListResponse> {
	return authenticatedFetch(`${API_BASE}/admin/users`, {
		method: 'GET'
	});
}

export async function activateUser(userId: string): Promise<User> {
	return authenticatedFetch(`${API_BASE}/admin/users/${userId}/activate`, {
		method: 'PUT'
	});
}

export async function deactivateUser(userId: string): Promise<User> {
	return authenticatedFetch(`${API_BASE}/admin/users/${userId}/deactivate`, {
		method: 'PUT'
	});
}

export async function deleteUser(userId: string): Promise<void> {
	await authenticatedFetch(`${API_BASE}/admin/users/${userId}`, {
		method: 'DELETE'
	});
}
