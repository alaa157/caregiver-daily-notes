// Shared API DTO skeleton. Backend contracts are provisional until Part B
// reconciles them against backend OpenAPI (Task B9).

export interface ApiError {
  code: string;
  message: string;
}

export interface Paginated<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface Recipient {
  id: string;
  name: string;
  active: boolean;
}

export interface Note {
  id: string;
  recipientId: string;
  date: string;
  mood: string;
  appetite: string;
  sleep: string;
  mobility: string;
  medicationTaken: string;
  pain: number;
  fall: boolean;
  text: string;
}

export interface Addendum {
  id: string;
  noteId: string;
  createdAt: string;
  text: string;
}

export interface Evidence {
  noteId: string;
  quote: string;
}

export interface Uncertainty {
  topic: string;
  detail: string;
}

export interface Summary {
  id: string;
  recipientId: string;
  periodDays: number;
  text: string;
  redFlags: string[];
  evidence: Evidence[];
  uncertainties: Uncertainty[];
}

export type PlanStatus =
  | 'Suggested'
  | 'Accepted'
  | 'Edited-and-Accepted'
  | 'Dismissed'
  | 'Archived';

export interface PlanVersion {
  version: number;
  status: PlanStatus;
  items: string[];
  createdAt: string;
  reason: string;
}

export interface Plan {
  id: string;
  recipientId: string;
  versions: PlanVersion[];
}

export type PlanAction = 'accept' | 'edit-accept' | 'dismiss' | 'archive';
