import axios from 'axios'

// 创建一个 axios 实例，配置 Spring Boot 的后端地址
const request = axios.create({
    baseURL: 'http://localhost:8080', // 指向你的 Java 后端
    timeout: 5000 // 请求超时时间 5 秒
})

export default request