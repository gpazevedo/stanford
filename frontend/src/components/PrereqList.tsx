// src/components/PrereqList.tsx
import type { PrereqStatus } from '@/types/api';

interface Props {
  prerequisites: PrereqStatus[];
}

export function PrereqList({ prerequisites }: Props) {
  if (prerequisites.length === 0) return null;
  return (
    <ul className="space-y-1">
      {prerequisites.map(p => (
        <li key={p.courseId} className="flex items-center gap-2 text-sm">
          <span className={p.met ? 'text-green-600 font-bold' : 'text-red-600 font-bold'}>
            {p.met ? '✓' : '✗'}
          </span>
          <span className={p.met ? 'text-gray-700' : 'text-gray-900 font-medium'}>
            {p.title}
          </span>
          <span className="text-gray-400 text-xs">({p.courseId})</span>
        </li>
      ))}
    </ul>
  );
}
