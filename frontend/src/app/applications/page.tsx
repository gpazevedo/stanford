// src/app/applications/page.tsx
'use client';
import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { WithdrawButton } from '@/components/WithdrawButton';
import { EligibilityBadge } from '@/components/EligibilityBadge';
import { getApplications, withdrawApplication } from '@/lib/api';
import type { CourseSearchResult } from '@/types/api';

export default function ApplicationsPage() {
  const [applications, setApplications] = useState<CourseSearchResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [withdrawing, setWithdrawing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getApplications()
      .then(setApplications)
      .catch(() => setError('Failed to load applications.'))
      .finally(() => setLoading(false));
  }, []);

  const handleWithdraw = useCallback(async (courseId: string) => {
    setWithdrawing(courseId);
    try {
      await withdrawApplication(courseId);
      setApplications(prev => prev.filter(a => a.courseId !== courseId));
    } catch {
      setError('Failed to withdraw application. Please try again.');
    } finally {
      setWithdrawing(null);
    }
  }, []);

  if (loading) return <p className="text-sm text-gray-500">Loading…</p>;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">My Applications</h1>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {applications.length === 0 ? (
        <p className="text-sm text-gray-500 py-8 text-center">
          No applications yet.{' '}
          <Link href="/" className="text-blue-600 hover:underline">Search for courses</Link>.
        </p>
      ) : (
        <div className="space-y-3">
          {applications.map(app => (
            <div
              key={app.courseId}
              className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm flex items-center justify-between gap-4"
            >
              <div>
                <Link
                  href={`/courses/${encodeURIComponent(app.courseId)}`}
                  className="font-semibold text-gray-900 hover:text-blue-600"
                >
                  {app.title}
                </Link>
                <p className="text-sm text-gray-500 mt-0.5">{app.units} units</p>
                <div className="mt-1">
                  <EligibilityBadge canApply={app.canApply} missingPrereqs={app.missingPrereqs} />
                </div>
              </div>
              <WithdrawButton
                courseId={app.courseId}
                onWithdraw={handleWithdraw}
                loading={withdrawing === app.courseId}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
