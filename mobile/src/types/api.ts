// Shared API DTO skeleton. Feature DTOs (Caregiver, Recipient, Note,
// Addendum, Summary, Plan, PlanVersion) grow here per feature task.

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
