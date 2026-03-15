// src/app/page.tsx
'use client';
import { useState, useCallback } from 'react';
import { SearchBar } from '@/components/SearchBar';
import { CourseCard } from '@/components/CourseCard';
import { searchCourses, applyToCourse } from '@/lib/api';
import type { CourseSearchResult } from '@/types/api';

export default function SearchPage() {
  const [results, setResults] = useState<CourseSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasSearched, setHasSearched] = useState(false);

  const handleSearch = useCallback(async (query: string) => {
    setLoading(true);
    setError(null);
    setHasSearched(true);
    try {
      setResults(await searchCourses(query, 10));
    } catch {
      setError('Search failed. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  const handleApply = useCallback(async (courseId: string) => {
    try {
      await applyToCourse(courseId);
      setResults(prev =>
        prev.map(c => c.courseId === courseId ? { ...c, applied: true } : c));
    } catch {
      setError('Could not apply. Please try again.');
    }
  }, []);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Find Courses</h1>
      <SearchBar onSearch={handleSearch} loading={loading} />
      {error && <p className="text-sm text-red-600">{error}</p>}
      {results.length > 0 && (
        <div className="space-y-3">
          {results.map(course => (
            <CourseCard
              key={course.courseId}
              course={course}
              onApply={handleApply}
            />
          ))}
        </div>
      )}
      {!loading && !hasSearched && (
        <p className="text-sm text-gray-500 text-center py-8">
          Enter a query above to discover courses.
        </p>
      )}
      {!loading && hasSearched && results.length === 0 && !error && (
        <p className="text-sm text-gray-500 text-center py-8">
          No courses found. Try different keywords.
        </p>
      )}
    </div>
  );
}
