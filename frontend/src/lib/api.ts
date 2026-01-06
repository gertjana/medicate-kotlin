export interface Medicine {
	id: string;
	name: string;
	dose: number;
	unit: string;
	stock: number;
}

export interface Schedule {
	id: string;
	medicineId: string;
	time: string;
	amount: number;
	daysOfWeek?: string;
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

const API_BASE = '/api';

// Medicine API
export async function getMedicines(): Promise<Medicine[]> {
	const response = await fetch(`${API_BASE}/medicine`);
	if (!response.ok) throw new Error('Failed to fetch medicines');
	return response.json();
}

export async function getMedicine(id: string): Promise<Medicine> {
	const response = await fetch(`${API_BASE}/medicine/${id}`);
	if (!response.ok) throw new Error('Failed to fetch medicine');
	return response.json();
}

export async function createMedicine(medicine: Omit<Medicine, 'id'>): Promise<Medicine> {
	const response = await fetch(`${API_BASE}/medicine`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(medicine)
	});
	if (!response.ok) throw new Error('Failed to create medicine');
	return response.json();
}

export async function updateMedicine(id: string, medicine: Medicine): Promise<Medicine> {
	const response = await fetch(`${API_BASE}/medicine/${id}`, {
		method: 'PUT',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(medicine)
	});
	if (!response.ok) throw new Error('Failed to update medicine');
	return response.json();
}

export async function deleteMedicine(id: string): Promise<void> {
	const response = await fetch(`${API_BASE}/medicine/${id}`, {
		method: 'DELETE'
	});
	if (!response.ok) throw new Error('Failed to delete medicine');
}

export async function addStock(medicineId: string, amount: number): Promise<Medicine> {
	const response = await fetch(`${API_BASE}/addstock`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ medicineId, amount })
	});
	if (!response.ok) throw new Error('Failed to add stock');
	return response.json();
}

// Schedule API
export async function getSchedules(): Promise<Schedule[]> {
	const response = await fetch(`${API_BASE}/schedule`);
	if (!response.ok) throw new Error('Failed to fetch schedules');
	return response.json();
}

export async function getSchedule(id: string): Promise<Schedule> {
	const response = await fetch(`${API_BASE}/schedule/${id}`);
	if (!response.ok) throw new Error('Failed to fetch schedule');
	return response.json();
}

export async function createSchedule(schedule: Omit<Schedule, 'id'>): Promise<Schedule> {
	const response = await fetch(`${API_BASE}/schedule`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(schedule)
	});
	if (!response.ok) throw new Error('Failed to create schedule');
	return response.json();
}

export async function updateSchedule(id: string, schedule: Schedule): Promise<Schedule> {
	const response = await fetch(`${API_BASE}/schedule/${id}`, {
		method: 'PUT',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify(schedule)
	});
	if (!response.ok) throw new Error('Failed to update schedule');
	return response.json();
}

export async function deleteSchedule(id: string): Promise<void> {
	const response = await fetch(`${API_BASE}/schedule/${id}`, {
		method: 'DELETE'
	});
	if (!response.ok) throw new Error('Failed to delete schedule');
}

// Daily schedule
export async function getDailySchedule(): Promise<DailySchedule> {
	const response = await fetch(`${API_BASE}/daily`);
	if (!response.ok) throw new Error('Failed to fetch daily schedule');
	return response.json();
}

// Dosage history
export async function takeDose(medicineId: string, amount: number, scheduledTime?: string, datetime?: string): Promise<DosageHistory> {
	const response = await fetch(`${API_BASE}/takedose`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ medicineId, amount, scheduledTime, datetime })
	});
	if (!response.ok) throw new Error('Failed to record dose');
	return response.json();
}

export async function getDosageHistories(): Promise<DosageHistory[]> {
	const response = await fetch(`${API_BASE}/history`);
	if (!response.ok) throw new Error('Failed to fetch dosage history');
	return response.json();
}
