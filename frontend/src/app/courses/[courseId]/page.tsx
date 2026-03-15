// src/app/courses/[courseId]/page.tsx
'use client';
import { useEffect, useState, useCallback } from 'react';
import { PrereqList } from '@/components/PrereqList';
import { getCourse, applyToCourse } from '@/lib/api';
import type { CourseDetail } from '@/types/api';
import { ApiError } from '@/types/api';

interface Props {
  params: Promise<{ courseId: string }>;
}

export default function CourseDetailPage({ params }: Props) {
  const [courseId, setCourseId] = useState<string | null>(null);
  const [course, setCourse] = useState<CourseDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    params.then(p => setCourseId(p.courseId));
  }, [params]);

  useEffect(() => {
    if (!courseId) return;
    getCourse(courseId)
      .then(setCourse)
      .catch(() => setError('Course not found.'));
  }, [courseId]);

  const handleApply = useCallback(async () => {
    if (!course || !courseId) return;
    try {
      await applyToCourse(courseId);
      setCourse(prev => prev ? { ...prev, applied: true } : prev);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        setError('Prerequisites not met.');
      } else {
        setError('Could not apply. Please try again.');
      }
    }
  }, [course, courseId]);

  if (!course && error) return <p className="text-sm text-red-600">{error}</p>;
  if (!course) return <p className="text-sm text-gray-500">Loading…</p>;

  const missingPrereqs = course.prerequisites
    .filter(p => !p.met).map(p => p.courseId);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">{course.title}</h1>
        <p className="text-sm text-gray-500 mt-1">{course.units} units · {course.quarter}</p>
        <p className="text-sm text-gray-500">{course.instructors.join(', ')}</p>
      </div>

      <p className="text-gray-700 leading-relaxed">{course.description}</p>

      {course.prerequisites.length > 0 && (
        <section>
          <h2 className="text-sm font-semibold text-gray-900 mb-2">Prerequisites</h2>
          <PrereqList prerequisites={course.prerequisites} />
          {course.prereqNote && (
            <p className="mt-2 text-xs text-gray-500 italic">{course.prereqNote}</p>
          )}
        </section>
      )}

      <div>
        {course.applied ? (
          <span className="inline-block rounded-full bg-gray-100 px-4 py-1.5 text-sm font-medium text-gray-600">
            Applied
          </span>
        ) : course.canApply ? (
          <button
            onClick={handleApply}
            className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            Apply
          </button>
        ) : (
          <p className="text-sm text-red-600 font-medium">
            {missingPrereqs.length > 0
              ? `Prerequisites not met — complete ${missingPrereqs.join(', ')} before applying.`
              : 'Prerequisites not met.'}
          </p>
        )}
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  );
}
