<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="login-title">数据智能分析平台</h2>
      <el-form
        ref="loginForm"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleLogin"
            style="width: 100%"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        <span>还没有账号？</span>
        <el-link type="primary" @click="goToRegister">立即注册</el-link>
      </div>
      <div class="login-tips">
        <p>默认账号：admin / admin123</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { authApi } from '@/api'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const loginForm = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度应为 3-50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度应为 6-100 个字符', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  try {
    await loginForm.value.validate()
    loading.value = true
    
    const res = await authApi.login(form.username, form.password)
    
    if (res.token) {
      localStorage.setItem('token', res.token)
      localStorage.setItem('user', JSON.stringify({
        id: res.id,
        username: res.username,
        email: res.email,
        role: res.role
      }))
      ElMessage.success('登录成功')
      router.push('/')
    }
  } catch (error) {
    console.error('登录失败:', error)
    // 显示后端返回的具体错误信息
    const errorMsg = error.response?.data?.message 
      || error.response?.data?.errors?.[0]?.message
      || '登录失败'
    ElMessage.error(errorMsg)
  } finally {
    loading.value = false
  }
}

const goToRegister = () => {
  router.push('/register')
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  padding: 20px;
}

.login-title {
  text-align: center;
  margin-bottom: 30px;
  color: #333;
  font-size: 24px;
}

.login-footer {
  margin-top: 20px;
  text-align: center;
  color: #666;
}

.login-footer .el-link {
  margin-left: 5px;
}

.login-tips {
  margin-top: 15px;
  text-align: center;
  color: #999;
  font-size: 12px;
}
</style>
