// src/components/CourseCard.tsx
import Link from 'next/link';
import { EligibilityBadge } from './EligibilityBadge';
import type { CourseSearchResult } from '@/types/api';

interface Props {
  course: CourseSearchResult;
  onApply: (courseId: string) => void;
}

export function CourseCard({ course, onApply }: Props) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm flex items-start justify-between gap-4">
      <div className="flex-1 min-w-0">
        <Link
          href={`/courses/${encodeURIComponent(course.courseId)}`}
          className="text-base font-semibold text-gray-900 hover:text-blue-600 truncate block"
        >
          {course.title}
        </Link>
        <p className="mt-0.5 text-sm text-gray-500">{course.units} units</p>
        <div className="mt-2">
          <EligibilityBadge canApply={course.canApply} missingPrereqs={course.missingPrereqs} />
        </div>
      </div>

      <div className="flex-shrink-0">
        {course.applied ? (
          <span className="inline-block rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600">
            Applied
          </span>
        ) : course.canApply ? (
          <button
            onClick={() => onApply(course.courseId)}
            className="rounded-lg bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
          >
            Apply
          </button>
        ) : null}
      </div>
    </div>
  );
}
