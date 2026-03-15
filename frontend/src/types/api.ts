// src/types/api.ts

export interface CourseSearchResult {
  courseId: string;
  title: string;
  units: string;
  canApply: boolean;
  missingPrereqs: string[];
  applied: boolean;
}

export interface PrereqStatus {
  courseId: string;
  title: string;
  met: boolean;
}

export interface CourseDetail {
  courseId: string;
  title: string;
  description: string;
  units: string;
  quarter: string;
  instructors: string[];
  prereqNote: string;
  prerequisites: PrereqStatus[];
  canApply: boolean;
  applied: boolean;
}

export interface AdminCourseView {
  courseId: string;
  title: string;
  applicantCount: number;
}

export interface CompletedCoursesResponse {
  courseIds: string[];
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}
