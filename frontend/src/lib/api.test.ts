// src/lib/api.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError } from '@/types/api';
import * as api from '@/lib/api';

// Mock auth module so tests don't hit Cognito
vi.mock('@/lib/auth', () => ({
  getCurrentJwt: vi.fn().mockResolvedValue('test-token'),
}));

// Use fetch mock via vi.stubGlobal
const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

beforeEach(() => mockFetch.mockReset());

function jsonResponse(data: unknown, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(data),
    text: () => Promise.resolve(JSON.stringify(data)),
  } as Response);
}

describe('searchCourses', () => {
  it('calls GET /courses/search with query and limit', async () => {
    mockFetch.mockReturnValueOnce(jsonResponse([{ courseId: 'CS229' }]));

    const results = await api.searchCourses('machine learning', 5);

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/courses/search?q=machine+learning&limit=5'),
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer test-token' }),
      })
    );
    expect(results[0].courseId).toBe('CS229');
  });

  it('throws ApiError on non-2xx response', async () => {
    mockFetch.mockReturnValueOnce(jsonResponse({ message: 'Not found' }, 404));

    await expect(api.searchCourses('x', 10)).rejects.toBeInstanceOf(ApiError);
  });
});

describe('applyToCourse', () => {
  it('calls POST /applications/{courseId} with auth header', async () => {
    mockFetch.mockReturnValueOnce(Promise.resolve({ ok: true, status: 201, text: () => Promise.resolve('') } as Response));

    await api.applyToCourse('CS229');

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/applications/CS229'),
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer test-token' }),
      })
    );
  });
});

describe('withdrawApplication', () => {
  it('calls DELETE /applications/{courseId}', async () => {
    mockFetch.mockReturnValueOnce(Promise.resolve({ ok: true, status: 204, text: () => Promise.resolve('') } as Response));

    await api.withdrawApplication('CS229');

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/applications/CS229'),
      expect.objectContaining({ method: 'DELETE' })
    );
  });
});
