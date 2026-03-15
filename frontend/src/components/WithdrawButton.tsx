// src/components/WithdrawButton.tsx
'use client';
import { useState } from 'react';

interface Props {
  courseId: string;
  onWithdraw: (courseId: string) => void;
  loading?: boolean;
}

export function WithdrawButton({ courseId, onWithdraw, loading = false }: Props) {
  const [confirming, setConfirming] = useState(false);

  if (confirming) {
    return (
      <span className="flex gap-2">
        <button
          onClick={() => { setConfirming(false); onWithdraw(courseId); }}
          disabled={loading}
          className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
        >
          Confirm
        </button>
        <button
          onClick={() => setConfirming(false)}
          disabled={loading}
          className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
        >
          Cancel
        </button>
      </span>
    );
  }

  return (
    <button
      onClick={() => setConfirming(true)}
      disabled={loading}
      className="rounded-lg border border-red-300 px-3 py-1.5 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-50"
    >
      Withdraw
    </button>
  );
}
