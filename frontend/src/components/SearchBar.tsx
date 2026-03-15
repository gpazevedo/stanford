'use client';
import { useState, FormEvent } from 'react';

interface Props {
  onSearch: (query: string) => void;
  loading: boolean;
  initialValue?: string;
}

export function SearchBar({ onSearch, loading, initialValue = '' }: Props) {
  const [query, setQuery] = useState(initialValue);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (query.trim()) onSearch(query.trim());
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-2 w-full">
      <input
        type="search"
        value={query}
        onChange={e => setQuery(e.target.value)}
        placeholder="Search courses (e.g. &quot;machine learning basics&quot;)"
        className="flex-1 rounded-lg border border-gray-300 px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <button
        type="submit"
        disabled={loading}
        className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 hover:bg-blue-700"
      >
        {loading ? 'Searching…' : 'Search'}
      </button>
    </form>
  );
}
