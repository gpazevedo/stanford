interface Props {
  canApply: boolean;
  missingPrereqs: string[];
}

export function EligibilityBadge({ canApply, missingPrereqs }: Props) {
  if (canApply) {
    return (
      <span className="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800">
        Eligible
      </span>
    );
  }
  const n = missingPrereqs.length;
  return (
    <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-800">
      Missing {n} prereq{n !== 1 ? 's' : ''}
    </span>
  );
}
