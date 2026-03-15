// src/lib/api.ts
import { getCurrentJwt } from '@/lib/auth';
import type {
  CourseSearchResult, CourseDetail,
  CompletedCoursesResponse,
} from '@/types/api';
import { ApiError } from '@/types/api';

const BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = await getCurrentJwt();
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...init.headers,
    },
  });
  if (!res.ok) {
    const msg = await res.text();
    throw new ApiError(res.status, msg);
  }
  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export function searchCourses(q: string, limit = 10): Promise<CourseSearchResult[]> {
  const params = new URLSearchParams({ q, limit: String(limit) });
  return apiFetch(`/courses/search?${params}`);
}

export function getCourse(courseId: string): Promise<CourseDetail> {
  return apiFetch(`/courses/${encodeURIComponent(courseId)}`);
}

export function getApplications(): Promise<CourseSearchResult[]> {
  return apiFetch('/applications');
}

export function applyToCourse(courseId: string): Promise<void> {
  return apiFetch(`/applications/${encodeURIComponent(courseId)}`, { method: 'POST' });
}

export function withdrawApplication(courseId: string): Promise<void> {
  return apiFetch(`/applications/${encodeURIComponent(courseId)}`, { method: 'DELETE' });
}

export function getCompletedCourses(): Promise<CompletedCoursesResponse> {
  return apiFetch('/profile/completed-courses');
}

export function updateCompletedCourses(courseIds: string[]): Promise<CompletedCoursesResponse> {
  return apiFetch('/profile/completed-courses', {
    method: 'PUT',
    body: JSON.stringify({ courseIds }),
  });
}
