import { reactive } from 'vue';

/**
 * 通用异步操作防重守卫。
 * 用法：
 *   const { locks, run } = useGuard();
 *   await run('save', async () => { ... });
 *   // 模板中  :loading="locks.save"  :disabled="locks.save"
 */
export function useGuard() {
  const locks = reactive<Record<string, boolean>>({});

  async function run(key: string, fn: () => Promise<void>) {
    if (locks[key]) return;
    locks[key] = true;
    try { await fn(); } finally { locks[key] = false; }
  }

  return { locks, run };
}
