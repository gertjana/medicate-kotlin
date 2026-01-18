export interface Medicine {
	id: string;
	name: string;
	dose: number;
	unit: string;
	stock: number;
	description?: string;
}

export interface User {
	username: string;
	email?: string;
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
}

// Determine API base URL based on environment
// - Server-side (SSR): Use internal backend URL (http://127.0.0.1:8080/api)
// - Client-side: Use relative URL (/api) which nginx proxies to backend
import { browser } from '$app/environment';

const API_BASE = browser
	? '/api'  // Client-side: relative URL, proxied by nginx
	: 'http://127.0.0.1:8080/api';  // Server-side: direct internal connection


// Helper function to get headers with JWT token
function getHeaders(includeContentType: boolean = false): HeadersInit {
	const headers: HeadersInit = {};

	// Get JWT token from localStorage (only available in browser)
	if (browser) {
		const token = localStorage.getItem('medicate_token');
		if (token) {
			headers['Authorization'] = `Bearer ${token}`;
		}
	}

	if (includeContentType) {
		headers['Content-Type'] = 'application/json';
	}

	return headers;
}

// Helper function to refresh the access token using refresh token
async function refreshAccessToken(): Promise<boolean> {
	if (!browser) return false;

	const refreshToken = localStorage.getItem('medicate_refresh_token');
	if (!refreshToken) {
		return false;
	}

	try {
		const response = await fetch(`${API_BASE}/auth/refresh`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ refreshToken })
		});

		if (!response.ok) {
			// Refresh token is also invalid - need to login again
			return false;
		}

		const data = await response.json();
		// Store new access token
		localStorage.setItem('medicate_token', data.token);
		return true;
	} catch (e) {
		console.error('Failed to refresh token:', e);
		return false;
	}
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
			localStorage.removeItem('medicate_token');
			localStorage.removeItem('medicate_refresh_token');
			localStorage.removeItem('medicate_user');
			// Reload to show login page
			window.location.reload();
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
			headers
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

export async function getLowStockMedicines(threshold: number = 10): Promise<Medicine[]> {
	return authenticatedFetch(`${API_BASE}/lowstock?threshold=${threshold}`, {
		cache: 'no-store'
	});
}

// User authentication API
export async function registerUser(username: string, password: string, email?: string): Promise<User> {
    const body: any = { username, password };
    if (email) body.email = email;

    const url = `${API_BASE}/user/register`;

    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to register user: ${response.status} ${response.statusText}`);
    }

    const authResponse: AuthResponse = await response.json();

    // Store user, access token, and refresh token in localStorage
    if (browser) {
        localStorage.setItem('medicate_user', JSON.stringify(authResponse.user));
        localStorage.setItem('medicate_token', authResponse.token);
        localStorage.setItem('medicate_refresh_token', authResponse.refreshToken);
    }

    return authResponse.user;
}

export async function loginUser(username: string, password: string): Promise<User> {
	const response = await fetch(`${API_BASE}/user/login`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ username, password })
	});
	if (!response.ok) throw new Error('Failed to login');

	const authResponse: AuthResponse = await response.json();

	// Store user, access token, and refresh token in localStorage
	if (browser) {
		localStorage.setItem('medicate_user', JSON.stringify(authResponse.user));
		localStorage.setItem('medicate_token', authResponse.token);
		localStorage.setItem('medicate_refresh_token', authResponse.refreshToken);
	}

	return authResponse.user;
}

export async function requestPasswordReset(username: string): Promise<{ message: string; emailId: string }> {
	const response = await fetch(`${API_BASE}/auth/resetPassword`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ username })
	});
	if (!response.ok) {
		const error = await response.json();
		throw new Error(error.error || 'Failed to request password reset');
	}
	return response.json();
}

export async function verifyResetToken(token: string): Promise<{ username: string }> {
	const response = await fetch(`${API_BASE}/auth/verifyResetToken`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ token })
	});
	if (!response.ok) {
		const error = await response.json();
		throw new Error(error.error || 'Invalid or expired token');
	}
	return response.json();
}

export async function updatePassword(username: string, newPassword: string): Promise<void> {
	const response = await fetch(`${API_BASE}/auth/updatePassword`, {
		method: 'PUT',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ username, password: newPassword })
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
export function logout(): void {
	if (browser) {
		localStorage.removeItem('medicate_user');
		localStorage.removeItem('medicate_token');
		localStorage.removeItem('medicate_refresh_token');
	}
}

// Helper to check if user is logged in
export function isLoggedIn(): boolean {
	if (!browser) return false;
	return localStorage.getItem('medicate_token') !== null;
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
