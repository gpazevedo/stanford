// src/app/profile/page.tsx
'use client';
import { useEffect, useState, useCallback, FormEvent } from 'react';
import { getCompletedCourses, updateCompletedCourses } from '@/lib/api';

export default function ProfilePage() {
  const [courseIds, setCourseIds] = useState<string[]>([]);
  const [newId, setNewId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getCompletedCourses()
      .then(r => setCourseIds(r.courseIds))
      .catch(() => setError('Failed to load completed courses.'));
  }, []);

  const save = useCallback(async (updated: string[]) => {
    setSaving(true);
    setError(null);
    try {
      const result = await updateCompletedCourses(updated);
      setCourseIds(result.courseIds);
    } catch {
      setError('Save failed. Check the course ID and try again.');
    } finally {
      setSaving(false);
    }
  }, []);

  const handleAdd = useCallback(async (e: FormEvent) => {
    e.preventDefault();
    const id = newId.trim().toUpperCase();
    if (!id) return;
    if (courseIds.includes(id)) {
      setError(`${id} is already in your completed courses.`);
      return;
    }
    setNewId('');
    await save([...courseIds, id]);
  }, [newId, courseIds, save]);

  const handleRemove = useCallback((id: string) => {
    save(courseIds.filter(c => c !== id));
  }, [courseIds, save]);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Profile</h1>

      <section>
        <h2 className="text-sm font-semibold text-gray-900 mb-3">Completed Courses</h2>
        <p className="text-xs text-gray-500 mb-4">
          Add courses you have already completed. This unlocks prerequisites for new applications.
        </p>

        {courseIds.length === 0 ? (
          <p className="text-sm text-gray-500">No completed courses recorded.</p>
        ) : (
          <ul className="space-y-2 mb-4">
            {courseIds.map(id => (
              <li key={id} className="flex items-center justify-between rounded-lg border border-gray-200 bg-white px-4 py-2">
                <span className="text-sm font-medium text-gray-900">{id}</span>
                <button
                  onClick={() => handleRemove(id)}
                  aria-label={`Remove ${id}`}
                  className="text-xs text-red-600 hover:text-red-800"
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}

        <form onSubmit={handleAdd} className="flex gap-2">
          <input
            value={newId}
            onChange={e => setNewId(e.target.value)}
            placeholder="Course ID (e.g. CS106B)"
            className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm"
          />
          <button
            type="submit"
            disabled={saving}
            className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 hover:bg-blue-700"
          >
            Add
          </button>
        </form>

        {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      </section>
    </div>
  );
}
