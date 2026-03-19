<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <i class="fa-solid fa-layer-group logo-icon"></i>
        <h2>注采生产监测与预警系统</h2>
      </div>

      <div class="mode-switch">
        <span :class="{ active: isLoginMode }" @click="isLoginMode = true">账号登录</span>
        <span :class="{ active: !isLoginMode }" @click="switchToRegister">账号注册</span>
      </div>

      <form v-if="isLoginMode" class="login-form" @submit.prevent="handleLogin">
        <div class="role-selector">
          <label class="radio-label">
            <input type="radio" v-model="formData.role" value="ADMIN"> 管理员
          </label>
          <label class="radio-label">
            <input type="radio" v-model="formData.role" value="USER"> 普通用户
          </label>
        </div>

        <div class="input-group">
          <i class="fa-solid fa-user"></i>
          <input type="text" v-model="formData.username" placeholder="请输入用户名" required />
        </div>
        <div class="input-group">
          <i class="fa-solid fa-lock"></i>
          <input type="password" v-model="formData.password" placeholder="请输入密码" required />
        </div>

        <button type="submit" class="login-btn">
          <span>登录系统</span> <i class="fa-solid fa-arrow-right-to-bracket"></i>
        </button>
      </form>

      <form v-else class="login-form" @submit.prevent="handleRegister">
        <div class="input-group">
          <i class="fa-solid fa-user"></i>
          <input type="text" v-model="formData.username" placeholder="设置用户名 (普通用户)" required />
        </div>
        <div class="input-group">
          <i class="fa-solid fa-lock"></i>
          <input type="password" v-model="formData.password" placeholder="设置密码" required />
        </div>

        <div class="captcha-group">
          <div class="input-group captcha-input">
            <i class="fa-solid fa-shield-halved"></i>
            <input type="text" v-model="inputCaptcha" placeholder="请输入右侧验证码" required />
          </div>
          <canvas ref="captchaCanvas" width="120" height="42" @click="drawCaptcha" class="captcha-canvas" title="点击刷新验证码"></canvas>
        </div>

        <button type="submit" class="login-btn register-btn">
          <span>立即注册</span> <i class="fa-solid fa-user-plus"></i>
        </button>
      </form>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()

const isLoginMode = ref(true)

const formData = ref({
  username: '',
  password: '',
  role: 'ADMIN' // 默认登录角色
})

// === 图形验证码核心逻辑 ===
const captchaCanvas = ref(null)
const actualCaptcha = ref('') // 真实的验证码答案
const inputCaptcha = ref('')  // 用户输入的验证码

// 切换到注册模式时，需要延迟一下等 canvas 渲染出来再去画图
const switchToRegister = async () => {
  isLoginMode.value = false
  inputCaptcha.value = ''
  await nextTick()
  drawCaptcha()
}

// 绘制基于 Canvas 的图形验证码
const drawCaptcha = () => {
  if (!captchaCanvas.value) return
  const ctx = captchaCanvas.value.getContext('2d')
  const width = 120
  const height = 42

  // 1. 生成 4 位随机字符 (去掉了容易混淆的 0, o, 1, i, l)
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  let code = ''
  for (let i = 0; i < 4; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  actualCaptcha.value = code

  // 2. 绘制背景色 (浅色)
  ctx.fillStyle = '#f8fafc'
  ctx.fillRect(0, 0, width, height)

  // 3. 绘制干扰线
  for (let i = 0; i < 5; i++) {
    ctx.strokeStyle = `rgba(${Math.random()*255},${Math.random()*255},${Math.random()*255}, 0.5)`
    ctx.beginPath()
    ctx.moveTo(Math.random() * width, Math.random() * height)
    ctx.lineTo(Math.random() * width, Math.random() * height)
    ctx.stroke()
  }

  // 4. 绘制干扰噪点
  for (let i = 0; i < 30; i++) {
    ctx.fillStyle = `rgba(${Math.random()*255},${Math.random()*255},${Math.random()*255}, 0.8)`
    ctx.beginPath()
    ctx.arc(Math.random() * width, Math.random() * height, 1, 0, 2 * Math.PI)
    ctx.fill()
  }

  // 5. 绘制文字 (带旋转和颜色扭曲)
  for (let i = 0; i < 4; i++) {
    ctx.font = 'bold 24px Arial'
    ctx.fillStyle = `rgb(${Math.floor(Math.random()*150)},${Math.floor(Math.random()*150)},${Math.floor(Math.random()*150)})`
    ctx.save()
    // 随机倾斜文字
    ctx.translate(25 * i + 15, 28)
    ctx.rotate((Math.random() - 0.5) * 0.4)
    ctx.fillText(code[i], 0, 0)
    ctx.restore()
  }
}

// === 登录逻辑 ===
const handleLogin = async () => {
  try {
    const res = await request.post('/api/user/login', formData.value)
    // 就是下面这行刚才可能被不小心删掉了，这行非常关键！
    const resultData = res.data !== undefined ? res.data : res

    if (resultData && resultData.success) {
      localStorage.setItem('userRole', resultData.role)
      // 🌟 核心修复：直接存入登录时输入的账号名称，不再用 realName 🌟
      localStorage.setItem('userName', formData.value.username)

      if (resultData.role === 'ADMIN') {
        router.push('/')
      } else {
        router.push('/history')
      }
    } else {
      alert(resultData ? resultData.message : '登录失败，请检查填写信息')
    }
  } catch (error) {
    console.error("🚨 登录报错：", error)
    alert('网络异常，请联系系统管理员！')
  }
}

// === 注册逻辑 ===
const handleRegister = async () => {
  // 🌟 1. 首先进行安全校验：对比验证码 (转为小写对比，不区分大小写)
  if (inputCaptcha.value.toLowerCase() !== actualCaptcha.value.toLowerCase()) {
    alert('❌ 验证码输入错误，请重新输入！')
    drawCaptcha() // 刷新验证码
    inputCaptcha.value = ''
    return
  }

  try {
    // 🌟 2. 构造专门用于注册的数据包：强制绑定为 USER，真实姓名暂存为用户名
    const registerPayload = {
      username: formData.value.username,
      password: formData.value.password,
      role: 'USER',  // 强制后台注册为普通用户！
      realName: formData.value.username
    }

    const res = await request.post('/api/user/register', registerPayload)
    const resultData = res.data !== undefined ? res.data : res

    if (resultData && resultData.success) {
      alert('🎉 注册成功！您已成为本系统的普通用户，即将为您切换到登录界面。')
      formData.value.password = ''
      formData.value.role = 'USER' // 切换回登录模式时，贴心地帮他选好普通用户
      isLoginMode.value = true
    } else {
      alert(resultData ? resultData.message : '注册失败')
      drawCaptcha() // 注册失败也刷新一下验证码
    }
  } catch (error) {
    console.error("🚨 注册报错：", error)
    alert('网络异常，请重试！')
  }
}
</script>

<style scoped>
/* 原有的背景、容器样式保持不变 */
.login-container { height: 100vh; width: 100vw; display: flex; justify-content: center; align-items: center; background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%); }
.login-box { background: rgba(255, 255, 255, 0.95); padding: 40px 50px; border-radius: 12px; box-shadow: 0 20px 50px rgba(0,0,0,0.5); width: 420px; text-align: center; transition: all 0.3s ease; }
.login-header { margin-bottom: 25px; }
.logo-icon { font-size: 40px; color: #3498db; margin-bottom: 15px; }
.login-header h2 { margin: 0; color: #1e293b; font-size: 22px; }
.login-header p { margin: 5px 0 0 0; color: #64748b; font-size: 14px; letter-spacing: 1px; }

/* 模式切换栏 */
.mode-switch { display: flex; justify-content: center; gap: 30px; margin-bottom: 25px; border-bottom: 2px solid #e2e8f0; padding-bottom: 10px; }
.mode-switch span { font-size: 16px; color: #94a3b8; cursor: pointer; font-weight: bold; position: relative; transition: color 0.3s; }
.mode-switch span:hover { color: #3498db; }
.mode-switch span.active { color: #3498db; }
.mode-switch span.active::after { content: ''; position: absolute; bottom: -12px; left: 0; width: 100%; height: 3px; background-color: #3498db; border-radius: 3px; }

/* 角色选择器 */
.role-selector { display: flex; justify-content: center; gap: 20px; margin-bottom: 20px; background: #f8fafc; padding: 10px; border-radius: 8px; border: 1px dashed #cbd5e1; }
.radio-label { font-size: 14px; color: #475569; cursor: pointer; display: flex; align-items: center; gap: 6px; font-weight: bold; }
.radio-label input[type="radio"] { accent-color: #3498db; cursor: pointer; }

/* 基础输入框 */
.input-group { position: relative; margin-bottom: 20px; width: 100%;}
.input-group i { position: absolute; left: 15px; top: 50%; transform: translateY(-50%); color: #94a3b8; }
.input-group input { width: 100%; padding: 12px 15px 12px 40px; border: 1px solid #cbd5e1; border-radius: 8px; box-sizing: border-box; font-size: 15px; outline: none; transition: all 0.3s; }
.input-group input:focus { border-color: #3498db; box-shadow: 0 0 0 3px rgba(52, 152, 219, 0.2); }

/* 🌟 图形验证码专属排版 🌟 */
.captcha-group { display: flex; gap: 10px; align-items: center; margin-bottom: 20px; }
.captcha-input { margin-bottom: 0 !important; flex: 1; }
.captcha-canvas { border: 1px solid #cbd5e1; border-radius: 8px; cursor: pointer; transition: 0.2s; box-shadow: 0 2px 5px rgba(0,0,0,0.05);}
.captcha-canvas:hover { border-color: #3498db; box-shadow: 0 2px 8px rgba(52, 152, 219, 0.2); }

/* 按钮 */
.login-btn { width: 100%; padding: 12px; background: #3498db; color: white; border: none; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; display: flex; justify-content: center; align-items: center; gap: 10px; transition: 0.2s; }
.login-btn:hover { background: #2980b9; transform: translateY(-2px); }
.register-btn { background: #27ae60; }
.register-btn:hover { background: #219653; }
</style>