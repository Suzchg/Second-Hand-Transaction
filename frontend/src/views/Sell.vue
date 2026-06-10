<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'
import { useRouter } from 'vue-router'
import ImageUploader from '../components/ImageUploader.vue'

const router = useRouter()
const title = ref('')
const priceYuan = ref('')
const description = ref('')
const quantity = ref(1)
const categoryId = ref(null)
const condition = ref(null)
const freeShipping = ref(false)
const shippingFeeYuan = ref('')
const categories = ref([])
const loading = ref(false)
const error = ref('')
const createdId = ref(null)

onMounted(async () => {
  try { categories.value = await api('/api/categories', { auth: false }) || [] }
  catch { /* ignore */ }
})

async function submit() {
  error.value = ''
  if (!title.value.trim()) { error.value = '请输入标题'; return }
  const priceCent = Math.round(Number(priceYuan.value || '0') * 100)
  if (!priceCent || priceCent <= 0) { error.value = '请输入有效价格'; return }
  if (!description.value.trim()) { error.value = '请输入描述'; return }
  loading.value = true
  try {
    const p = await api('/api/products', {
      method: 'POST',
      body: {
        title: title.value.trim(),
        priceCent,
        quantity: quantity.value,
        coverImageUrl: '',
        description: description.value.trim(),
        categoryId: categoryId.value,
        condition: condition.value,
        freeShipping: freeShipping.value === true,
        shippingFeeCent: freeShipping.value ? 0 : Math.round(Number(shippingFeeYuan.value || '0') * 100),
      },
    })
    createdId.value = p.id
  } catch (e) {
    error.value = e.message || '发布失败'
  } finally {
    loading.value = false
  }
}

function done() { router.push(`/products/${createdId.value}`) }
function skip() { router.push('/my-products') }
function cancel() { router.go(-1) }
</script>

<template>
  <div class="sellPage">
    <h2 class="pageTitle"><AppIcon name="edit" :size="20"/> 发布闲置</h2>

    <!-- Step 1: 填写信息 -->
    <div v-if="!createdId" class="sellCard">
      <div class="formGrid">
        <label>商品标题
          <input v-model="title" placeholder="例如：二手 iPhone 13，九成新，发票齐全" />
        </label>

        <label>分类
          <select v-model="categoryId" class="selInput">
            <option :value="null">请选择分类（可选）</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.iconUrl }} {{ c.name }}</option>
          </select>
        </label>

        <div class="formRow">
          <label>价格（元）
            <input v-model="priceYuan" type="number" step="0.01" min="0" placeholder="例如：1999.00" />
          </label>
          <label>库存
            <input v-model.number="quantity" type="number" min="1" max="999" placeholder="1" />
          </label>
        </div>

        <label class="checkLabel">
          <input type="checkbox" v-model="freeShipping" />
          <span>包邮（免邮费）</span>
        </label>

        <label v-if="!freeShipping">邮费（元）
          <input v-model="shippingFeeYuan" type="number" step="1" min="0" placeholder="例如：10" />
        </label>

        <label>成色
          <select v-model="condition" class="selInput">
            <option :value="null">请选择成色（可选）</option>
            <option value="NEW">全新</option>
            <option value="LIKE_NEW">99新</option>
            <option value="NINE_TENTHS">9成新</option>
            <option value="EIGHT_TENTHS">8成新</option>
            <option value="SEVEN_TENTHS">7成新</option>
            <option value="SIX_TENTHS_AND_BELOW">6成新及以下</option>
          </select>
        </label>

        <label>描述
          <textarea v-model="description" rows="4" placeholder="描述商品成色、规格、配件、使用情况等信息..." />
        </label>
      </div>

      <p v-if="error" class="errMsg">{{ error }}</p>

      <div class="formActions">
        <button class="submitBtn" :disabled="loading" @click="submit">
          {{ loading ? '创建中...' : '🚀 发布商品' }}
        </button>
        <button class="cancelBtn" @click="cancel">取消</button>
      </div>
    </div>

    <!-- Step 2: 上传图片 -->
    <div v-else class="sellCard uploadStep">
      <div class="successBanner">
        <span class="successIcon"><AppIcon name="check" :size="48"/></span>
        <span>商品已发布！上传实物图片（最多 9 张），第一张自动成为封面</span>
      </div>
      <ImageUploader :productId="createdId" />
      <div class="actions">
        <button class="submitBtn" @click="done">查看商品</button>
        <button class="cancelBtn" @click="skip">稍后再说</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sellPage { max-width: 560px; margin: 0 auto; }

.pageTitle { font-size: 20px; font-weight: 700; margin: 0 0 var(--space-xl); }

.sellCard {
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: var(--space-2xl);
  box-shadow: var(--shadow-sm);
}

.formGrid { display: grid; gap: var(--space-lg); }

.formGrid label {
  display: grid;
  gap: var(--space-xs);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.formGrid input, .formGrid textarea, .selInput {
  width: 100%;
  box-sizing: border-box;
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: 14px;
  font-family: inherit;
  color: var(--text-primary);
  background: var(--bg-tertiary);
  transition: all var(--transition-fast);
}

.formGrid input:focus, .formGrid textarea:focus, .selInput:focus {
  background: var(--bg-primary);
}

.selInput { background: var(--bg-tertiary); cursor: pointer; }

.formGrid textarea { resize: vertical; min-height: 90px; }

.formRow {
  display: grid;
  grid-template-columns: 1fr 120px;
  gap: var(--space-md);
}

.checkLabel {
  display: flex !important;
  align-items: center;
  gap: var(--space-sm) !important;
  cursor: pointer;
  font-size: 14px !important;
}

.checkLabel input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--brand-dark); }

.errMsg { color: var(--error); font-size: 13px; margin: var(--space-md) 0 0; }

.formActions {
  display: flex;
  gap: var(--space-md);
  margin-top: var(--space-xl);
}

.submitBtn {
  flex: 1;
  border: none;
  background: var(--brand-gradient);
  color: white;
  padding: 13px 24px;
  border-radius: var(--radius-md);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow-brand);
  transition: all var(--transition-fast);
}

.submitBtn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
}

.submitBtn:disabled { opacity: 0.5; cursor: not-allowed; }

.cancelBtn {
  border: 1.5px solid var(--border-default);
  background: var(--bg-primary);
  padding: 13px 20px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
}

.cancelBtn:hover { border-color: var(--border-strong); }

.successBanner {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  background: var(--success-bg);
  border: 1px solid var(--success-border);
  border-radius: var(--radius-md);
  color: var(--success);
  font-size: 14px;
  margin-bottom: var(--space-lg);
}

.successIcon { font-size: 18px; }

.actions {
  display: flex;
  gap: var(--space-md);
  margin-top: var(--space-xl);
}

.uploadStep .submitBtn { flex: 1; }

@media (max-width: 480px) {
  .sellCard { padding: var(--space-lg); }
  .formRow { grid-template-columns: 1fr; }
}
</style>
