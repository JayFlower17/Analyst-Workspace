import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 120000,  // 增加到 120 秒，匹配后端超时
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器 - 自动添加 JWT token
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    // 处理 401 未授权错误，跳转到登录页
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// 数据集相关API
export const datasetApi = {
  // 获取所有数据集
  getAll(groupId) {
    return api.get('/datasets', {
      params: groupId ? { groupId } : {}
    })
  },
  
  // 获取单个数据集
  getById(id) {
    return api.get(`/datasets/${id}`)
  },
  
  // 获取数据集元数据
  getMetadata(id) {
    return api.get(`/datasets/${id}/metadata`)
  },
  
  // 上传数据集
  upload(file, name, groupId) {
    const formData = new FormData()
    formData.append('file', file)
    if (name) {
      formData.append('name', name)
    }
    if (groupId) {
      formData.append('groupId', groupId)
    }
    return api.post('/datasets/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },
  
  // 删除数据集
  delete(id) {
    return api.delete(`/datasets/${id}`)
  },

  // 更新数据表 markdown 描述
  updateDescription(id, descriptionMd) {
    return api.put(`/datasets/${id}/description`, { descriptionMd })
  }
}

export const groupApi = {
  create(payload) {
    return api.post('/groups', payload)
  },

  getAll() {
    return api.get('/groups')
  },

  getById(id) {
    return api.get(`/groups/${id}`)
  },

  updateDescription(id, descriptionMd) {
    return api.put(`/groups/${id}/description`, { descriptionMd })
  },

  getDatasets(id) {
    return api.get(`/groups/${id}/datasets`)
  },

  getRelations(id) {
    return api.get(`/groups/${id}/relations`)
  },

  autoDetectRelations(id) {
    return api.post(`/groups/${id}/relations/auto-detect`)
  },

  createRelation(id, payload) {
    return api.post(`/groups/${id}/relations`, payload)
  },

  updateRelation(id, relationId, payload) {
    return api.put(`/groups/${id}/relations/${relationId}`, payload)
  },

  deleteRelation(id, relationId) {
    return api.delete(`/groups/${id}/relations/${relationId}`)
  }
}

export const chatApi = {
  createSession(title) {
    return api.post('/chat/sessions', { title })
  },

  getSessions() {
    return api.get('/chat/sessions')
  },

  getMessages(sessionId) {
    return api.get(`/chat/sessions/${sessionId}/messages`)
  },

  getDatasets(sessionId) {
    return api.get(`/chat/sessions/${sessionId}/datasets`)
  },

  upload(sessionId, file, name) {
    const formData = new FormData()
    formData.append('file', file)
    if (name) {
      formData.append('name', name)
    }
    return api.post(`/chat/sessions/${sessionId}/upload`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  analyze(sessionId, query, datasetId) {
    return api.post(`/chat/sessions/${sessionId}/analyze`, {
      query,
      datasetId
    })
  },

  promote(sessionId, payload) {
    return api.post(`/chat/sessions/${sessionId}/promote-to-workspace`, payload)
  }
}

// 分析相关API
export const analysisApi = {
  // 执行分析查询
  query(groupId, query, focusDatasetIds = []) {
    return api.post('/analysis/query', {
      groupId,
      query,
      focusDatasetIds
    })
  },
  
  // 预览数据
  preview(datasetId, limit = 100) {
    return api.get(`/analysis/preview/${datasetId}`, {
      params: { limit }
    })
  }
}

// 认证相关API
export const authApi = {
  // 登录
  login(username, password) {
    return api.post('/auth/signin', { username, password })
  },
  
  // 注册
  register(username, email, password, confirmPassword) {
    return api.post('/auth/signup', { username, email, password, confirmPassword })
  }
}

export default api
