import { createRouter, createWebHistory } from 'vue-router'

const whiteList = ['/login', '/register']

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/login/RegisterView.vue'),
      meta: { title: '注册' },
    },
    {
      path: '/',
      component: () => import('@/layouts/BasicLayout.vue'),
      redirect: '/chat',
      children: [
        {
          path: 'app',
          name: 'AppManage',
          component: () => import('@/views/app/AppManageView.vue'),
          meta: { title: '应用管理' },
        },
        {
          path: 'app/deploy',
          name: 'AppDeployHome',
          component: () => import('@/views/app/deploy/AppDeployView.vue'),
          meta: { title: '部署分享' },
        },
        {
          path: 'app/:id/deploy',
          name: 'AppDeploy',
          component: () => import('@/views/app/deploy/AppDeployView.vue'),
          meta: { title: '部署分享' },
        },
        {
          path: 'app/gallery',
          name: 'AppGallery',
          component: () => import('@/views/app/AppGalleryView.vue'),
          meta: { title: '精选广场' },
        },
        {
          path: 'user',
          name: 'UserManage',
          component: () => import('@/views/user/UserManageView.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/user/ProfileView.vue'),
          meta: { title: '个人信息' },
        },
        {
          path: 'codegen',
          name: 'CodeGen',
          component: () => import('@/views/codegen/CodeGenView.vue'),
          meta: { title: '代码生成' },
        },
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('@/views/chat/ChatView.vue'),
          meta: { title: '首页' }
        }
      ],
    },
  ],
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || 'AI 代码生成平台'} - AI Agent Platform`
  const token = localStorage.getItem('token')
  if (token) {
    if (to.path === '/login' || to.path === '/register') {
      next('/')
    } else {
      next()
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router
