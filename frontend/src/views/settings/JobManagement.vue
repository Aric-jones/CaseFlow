<template>
  <div class="page-wrap" style="padding:20px;background:#f0f2f5;height:100%;overflow:auto">
    <div class="content-card">
      <div class="tab-toolbar">
        <el-button type="primary" :icon="Plus" @click="openForm()">新增任务</el-button>
      </div>

      <el-table :data="jobs" v-loading="loading" border style="width:100%">
        <el-table-column label="任务名称" prop="jobName" min-width="150" />
        <el-table-column label="任务分组" prop="jobGroup" min-width="100" />
        <el-table-column label="调用目标" prop="invokeTarget" min-width="200" show-overflow-tooltip />
        <el-table-column label="cron表达式" prop="cronExpression" min-width="140" />
        <el-table-column label="状态" min-width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 0" @change="toggleStatus(row)"
              active-text="正常" inactive-text="暂停" inline-prompt />
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button text type="success" size="small" :loading="locks['run_' + row.id]" @click="runOnce(row)">执行一次</el-button>
            <el-button text type="info" size="small" @click="openLogs(row)">日志</el-button>
            <el-popconfirm title="确认删除该任务？" @confirm="deleteJob(row.id)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="formData.id ? '编辑任务' : '新增任务'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="formData.jobName" />
        </el-form-item>
        <el-form-item label="任务分组">
          <el-input v-model="formData.jobGroup" placeholder="DEFAULT" />
        </el-form-item>
        <el-form-item label="调用目标" required>
          <el-input v-model="formData.invokeTarget" placeholder="beanName.methodName" />
        </el-form-item>
        <el-form-item label="cron表达式" required>
          <el-input v-model="formData.cronExpression" placeholder="0 0 2 * * ?" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">暂停</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="locks.saveJob" @click="saveJob">确定</el-button>
      </template>
    </el-dialog>

    <!-- 日志 -->
    <el-dialog v-model="logVisible" :title="'执行日志 - ' + logJobName" width="700px">
      <el-table :data="logData.records" v-loading="logLoading" border size="small">
        <el-table-column label="状态" min-width="60" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
              {{ row.status === 0 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行结果" prop="message" min-width="200" show-overflow-tooltip />
        <el-table-column label="异常信息" prop="exception" min-width="180" show-overflow-tooltip />
        <el-table-column label="开始时间" min-width="150">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="150">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="logData.pages > 1" style="margin-top:12px;display:flex;justify-content:center">
        <el-pagination small layout="prev, pager, next" :total="logData.total"
          :page-size="20" :current-page="logData.current"
          @current-change="(p: number) => loadLogs(logJobId, p)" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { useGuard } from '../../composables/useGuard';
import request from '../../api/request';

const { locks, run } = useGuard();
const loading = ref(false);
const jobs = ref<any[]>([]);

const formVisible = ref(false);
const formData = ref<any>({ jobName: '', jobGroup: 'DEFAULT', invokeTarget: '', cronExpression: '', status: 0, remark: '' });

const logVisible = ref(false);
const logLoading = ref(false);
const logJobId = ref('');
const logJobName = ref('');
const logData = ref<any>({ records: [], total: 0, current: 1, pages: 0 });

async function loadJobs() {
  loading.value = true;
  try {
    const res = await request.get('/sys-jobs');
    jobs.value = res.data;
  } finally { loading.value = false; }
}

function openForm(row?: any) {
  if (row) {
    formData.value = { ...row };
  } else {
    formData.value = { jobName: '', jobGroup: 'DEFAULT', invokeTarget: '', cronExpression: '', status: 0, remark: '' };
  }
  formVisible.value = true;
}

async function saveJob() {
  const d = formData.value;
  if (!d.jobName?.trim() || !d.invokeTarget?.trim() || !d.cronExpression?.trim()) {
    ElMessage.error('请填写必填项'); return;
  }
  await run('saveJob', async () => {
    if (d.id) {
      await request.put(`/sys-jobs/${d.id}`, d);
    } else {
      await request.post('/sys-jobs', d);
    }
    ElMessage.success('保存成功');
    formVisible.value = false;
    loadJobs();
  });
}

async function deleteJob(id: string) {
  await run('deleteJob', async () => {
    await request.delete(`/sys-jobs/${id}`);
    ElMessage.success('删除成功'); loadJobs();
  });
}

async function toggleStatus(row: any) {
  const newStatus = row.status === 0 ? 1 : 0;
  await run('toggleStatus', async () => {
    await request.put(`/sys-jobs/${row.id}/status`, null, { params: { status: newStatus } });
    row.status = newStatus;
    ElMessage.success(newStatus === 0 ? '已启用' : '已暂停');
  });
}

async function runOnce(row: any) {
  await run('run_' + row.id, async () => {
    await request.post(`/sys-jobs/${row.id}/run`);
    ElMessage.success('执行完成');
  });
}

function openLogs(row: any) {
  logJobId.value = row.id;
  logJobName.value = row.jobName;
  logVisible.value = true;
  loadLogs(row.id);
}

async function loadLogs(jobId: string, page = 1) {
  logLoading.value = true;
  try {
    const res = await request.get(`/sys-jobs/${jobId}/logs`, { params: { page, size: 20 } });
    logData.value = res.data;
  } finally { logLoading.value = false; }
}

function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }

onMounted(loadJobs);
</script>

<style scoped>
.content-card { background: #fff; border-radius: 8px; padding: 20px; }
.tab-toolbar { margin-bottom: 16px; display: flex; gap: 8px; }
</style>
