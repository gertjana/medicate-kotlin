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
	token: string;
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

// Medicine API
export async function getMedicines(): Promise<Medicine[]> {
	const response = await fetch(`${API_BASE}/medicine`, {
		cache: 'no-store',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to fetch medicines');
	return response.json();
}

export async function getMedicine(id: string): Promise<Medicine> {
	const response = await fetch(`${API_BASE}/medicine/${id}`, {
		cache: 'no-store',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to fetch medicine');
	return response.json();
}

export async function createMedicine(medicine: Omit<Medicine, 'id'>): Promise<Medicine> {
	const response = await fetch(`${API_BASE}/medicine`, {
		method: 'POST',
		headers: getHeaders(true),
		body: JSON.stringify(medicine)
	});
	if (!response.ok) throw new Error('Failed to create medicine');
	return response.json();
}

export async function updateMedicine(id: string, medicine: Medicine): Promise<Medicine> {
	const response = await fetch(`${API_BASE}/medicine/${id}`, {
		method: 'PUT',
		headers: getHeaders(true),
		body: JSON.stringify(medicine)
	});
	if (!response.ok) throw new Error('Failed to update medicine');
	return response.json();
}

export async function deleteMedicine(id: string): Promise<void> {
	const response = await fetch(`${API_BASE}/medicine/${id}`, {
		method: 'DELETE',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to delete medicine');
}

export async function addStock(medicineId: string, amount: number): Promise<Medicine> {
	const response = await fetch(`${API_BASE}/addstock`, {
		method: 'POST',
		headers: getHeaders(true),
		body: JSON.stringify({ medicineId, amount })
	});
	if (!response.ok) throw new Error('Failed to add stock');
	return response.json();
}

// Schedule API
export async function getSchedules(): Promise<Schedule[]> {
	const response = await fetch(`${API_BASE}/schedule`, {
		cache: 'no-store',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to fetch schedules');
	return response.json();
}

export async function getSchedule(id: string): Promise<Schedule> {
	const response = await fetch(`${API_BASE}/schedule/${id}`, {
		cache: 'no-store',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to fetch schedule');
	return response.json();
}

export async function createSchedule(schedule: Omit<Schedule, 'id'>): Promise<Schedule> {
	const response = await fetch(`${API_BASE}/schedule`, {
		method: 'POST',
		headers: getHeaders(true),
		body: JSON.stringify(schedule)
	});
	if (!response.ok) throw new Error('Failed to create schedule');
	return response.json();
}

export async function updateSchedule(id: string, schedule: Schedule): Promise<Schedule> {
	const response = await fetch(`${API_BASE}/schedule/${id}`, {
		method: 'PUT',
		headers: getHeaders(true),
		body: JSON.stringify(schedule)
	});
	if (!response.ok) throw new Error('Failed to update schedule');
	return response.json();
}

export async function deleteSchedule(id: string): Promise<void> {
	const response = await fetch(`${API_BASE}/schedule/${id}`, {
		method: 'DELETE',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to delete schedule');
}

// Daily schedule
export async function getDailySchedule(): Promise<DailySchedule> {
	const response = await fetch(`${API_BASE}/daily`, {
		cache: 'no-store',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to fetch daily schedule');
	return response.json();
}

// Dosage history
export async function takeDose(medicineId: string, amount: number, scheduledTime?: string, datetime?: string): Promise<DosageHistory> {
	const response = await fetch(`${API_BASE}/takedose`, {
		method: 'POST',
		headers: getHeaders(true),
		body: JSON.stringify({ medicineId, amount, scheduledTime, datetime })
	});
	if (!response.ok) throw new Error('Failed to record dose');
	return response.json();
}

export async function getDosageHistories(): Promise<DosageHistory[]> {
	const response = await fetch(`${API_BASE}/history`, {
		cache: 'no-store',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to fetch dosage history');
	return response.json();
}

export async function deleteDosageHistory(id: string): Promise<void> {
	const response = await fetch(`${API_BASE}/history/${id}`, {
		method: 'DELETE',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to delete dosage history');
}


// Adherence and analytics
export async function getWeeklyAdherence(): Promise<WeeklyAdherence> {
	const response = await fetch(`${API_BASE}/adherence`, {
		cache: 'no-store',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to fetch weekly adherence');
	return response.json();
}

export async function getLowStockMedicines(threshold: number = 10): Promise<Medicine[]> {
	const response = await fetch(`${API_BASE}/lowstock?threshold=${threshold}`, {
		cache: 'no-store',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to fetch low stock medicines');
	return response.json();
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

    // Store both user and JWT token in localStorage
    if (browser) {
        localStorage.setItem('medicate_user', JSON.stringify(authResponse.user));
        localStorage.setItem('medicate_token', authResponse.token);
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

	// Store both user and JWT token in localStorage
	if (browser) {
		localStorage.setItem('medicate_user', JSON.stringify(authResponse.user));
		localStorage.setItem('medicate_token', authResponse.token);
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
	const response = await fetch(`${API_BASE}/medicineExpiry`, {
		cache: 'no-store',
		headers: getHeaders()
	});
	if (!response.ok) throw new Error('Failed to fetch medicine expiry');
	return response.json();
}

// Logout function to clear authentication
export function logout(): void {
	if (browser) {
		localStorage.removeItem('medicate_user');
		localStorage.removeItem('medicate_token');
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
