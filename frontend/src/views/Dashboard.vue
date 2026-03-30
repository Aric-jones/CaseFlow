<template>
  <div class="dashboard-page">
    <div class="welcome-banner">
      <div class="welcome-text">
        <h1>{{ greeting }}，{{ store.user?.displayName || '用户' }}</h1>
        <p>以下是您当前项目的工作概览</p>
      </div>
    </div>

    <div v-loading="loading" class="dashboard-content">
      <!-- 统计卡片 -->
      <div class="stat-cards">
        <div class="stat-card blue" @click="$router.push('/func-test/cases')">
          <div class="stat-icon"><el-icon :size="28"><Document /></el-icon></div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.myCreatedCaseSetCount ?? 0 }}</div>
            <div class="stat-label">我创建的用例集</div>
          </div>
        </div>
        <div class="stat-card orange">
          <div class="stat-icon"><el-icon :size="28"><Edit /></el-icon></div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.writingCaseSetCount ?? 0 }}</div>
            <div class="stat-label">编写中的用例集</div>
          </div>
        </div>
        <div class="stat-card purple">
          <div class="stat-icon"><el-icon :size="28"><Stamp /></el-icon></div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.pendingReviewCount ?? 0 }}</div>
            <div class="stat-label">待评审</div>
          </div>
        </div>
        <div class="stat-card green" @click="$router.push('/func-test/plans')">
          <div class="stat-icon"><el-icon :size="28"><Calendar /></el-icon></div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.myPlanCount ?? 0 }}</div>
            <div class="stat-label">我负责的测试计划</div>
          </div>
        </div>
        <div class="stat-card red">
          <div class="stat-icon"><el-icon :size="28"><Aim /></el-icon></div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.pendingExecuteCaseCount ?? 0 }}</div>
            <div class="stat-label">待执行用例</div>
          </div>
        </div>
      </div>

      <div class="panel-row">
        <!-- 待评审列表 -->
        <div class="panel">
          <div class="panel-header">
            <h3>待评审用例集</h3>
          </div>
          <div class="panel-body">
            <el-empty v-if="!reviewList.length" description="暂无待评审" :image-size="60" />
            <div v-else class="task-list">
              <div v-for="item in reviewList" :key="item.reviewId" class="task-item"
                   @click="$router.push('/review/' + item.caseSetId)">
                <div class="task-name">{{ item.caseSetName }}</div>
                <div class="task-meta">
                  <span>提交人：{{ item.createdByName }}</span>
                  <span>{{ fmtTime(item.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 测试计划执行进度 -->
        <div class="panel">
          <div class="panel-header">
            <h3>我的测试计划进度</h3>
          </div>
          <div class="panel-body">
            <el-empty v-if="!planProgress.length" description="暂无测试任务" :image-size="60" />
            <div v-else class="task-list">
              <div v-for="p in planProgress" :key="p.planId" class="task-item"
                   @click="$router.push('/test-plan/' + p.planId + '/execute')">
                <div class="task-name">{{ p.planName }}</div>
                <div class="progress-row">
                  <el-progress :percentage="p.total ? Math.round(((p.pass + p.fail + p.skip) / p.total) * 100) : 0"
                               :stroke-width="10" style="flex:1">
                    <span style="font-size:12px">
                      {{ p.pass + p.fail + p.skip }}/{{ p.total }}
                    </span>
                  </el-progress>
                </div>
                <div class="progress-detail">
                  <span class="tag pass">通过 {{ p.pass }}</span>
                  <span class="tag fail">失败 {{ p.fail }}</span>
                  <span class="tag skip">跳过 {{ p.skip }}</span>
                  <span class="tag pending">待执行 {{ p.pending }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Document, Edit, Stamp, Calendar, Aim } from '@element-plus/icons-vue';
import { dashboardApi } from '../api';
import { useAppStore } from '../stores/app';

const store = useAppStore();
const loading = ref(false);
const stats = ref<any>({});
const reviewList = computed(() => stats.value.pendingReviewList || []);
const planProgress = computed(() => stats.value.planProgress || []);

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

async function load() {
  if (!store.currentProject) return;
  loading.value = true;
  try { stats.value = (await dashboardApi.stats(store.currentProject.id)).data; }
  catch { ElMessage.error('加载工作台数据失败'); }
  finally { loading.value = false; }
}

watch(() => store.currentProject, load);
onMounted(load);

function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 16) : ''; }
</script>

<style scoped>
.dashboard-page { padding: 24px; height: 100%; overflow: auto; background: #f0f2f5; }

.welcome-banner {
  background: linear-gradient(135deg, #1677ff 0%, #4f9cf7 100%);
  border-radius: 12px;
  padding: 28px 32px;
  margin-bottom: 24px;
  color: #fff;
}
.welcome-text h1 { font-size: 22px; font-weight: 600; margin: 0 0 6px; }
.welcome-text p { margin: 0; font-size: 14px; opacity: 0.85; }

.stat-cards { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; margin-bottom: 24px; }
.stat-card {
  background: #fff; border-radius: 10px; padding: 20px;
  display: flex; align-items: center; gap: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06); cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.stat-icon { width: 52px; height: 52px; border-radius: 12px; display: flex; align-items: center; justify-content: center; }
.stat-card.blue .stat-icon { background: #e8f4ff; color: #1677ff; }
.stat-card.orange .stat-icon { background: #fff7e6; color: #fa8c16; }
.stat-card.purple .stat-icon { background: #f3e8ff; color: #722ed1; }
.stat-card.green .stat-icon { background: #e6fffb; color: #13c2c2; }
.stat-card.red .stat-icon { background: #fff1f0; color: #f5222d; }
.stat-number { font-size: 28px; font-weight: 700; color: #1f2329; line-height: 1.2; }
.stat-label { font-size: 13px; color: #8c8c8c; margin-top: 2px; }

.panel-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.panel { background: #fff; border-radius: 10px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); overflow: hidden; }
.panel-header { padding: 16px 20px; border-bottom: 1px solid #f0f0f0; }
.panel-header h3 { margin: 0; font-size: 15px; font-weight: 600; color: #1f2329; }
.panel-body { padding: 12px 20px 20px; max-height: 360px; overflow-y: auto; }

.task-list { display: flex; flex-direction: column; gap: 10px; }
.task-item {
  padding: 12px 14px; border-radius: 8px; border: 1px solid #f0f0f0;
  cursor: pointer; transition: background 0.2s, border-color 0.2s;
}
.task-item:hover { background: #f8fafc; border-color: #d9e3f0; }
.task-name { font-size: 14px; font-weight: 500; color: #1f2329; margin-bottom: 6px; }
.task-meta { font-size: 12px; color: #8c8c8c; display: flex; gap: 16px; }

.progress-row { display: flex; align-items: center; margin: 6px 0; }
.progress-detail { display: flex; gap: 8px; flex-wrap: wrap; }
.tag { font-size: 11px; padding: 2px 8px; border-radius: 4px; font-weight: 500; }
.tag.pass { background: #f6ffed; color: #52c41a; }
.tag.fail { background: #fff1f0; color: #f5222d; }
.tag.skip { background: #fffbe6; color: #faad14; }
.tag.pending { background: #f0f0f0; color: #8c8c8c; }

@media (max-width: 1200px) {
  .stat-cards { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 900px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .panel-row { grid-template-columns: 1fr; }
}
</style>
