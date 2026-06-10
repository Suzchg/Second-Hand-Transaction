import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  { path: '/', component: () => import('./views/Home.vue') },
  { path: '/products/:id', component: () => import('./views/ProductDetail.vue') },
  { path: '/sell', component: () => import('./views/Sell.vue') },
  { path: '/seller/:id', component: () => import('./views/SellerProducts.vue') },
  { path: '/my-products', component: () => import('./views/MyProducts.vue') },
  { path: '/messages', component: () => import('./views/Messages.vue') },
  { path: '/my-orders', component: () => import('./views/MyOrders.vue') },
  { path: '/my-after-sales', component: () => import('./views/MyAfterSales.vue') },
  { path: '/my-favorites', component: () => import('./views/MyFavorites.vue') },
  { path: '/switch-account', component: () => import('./views/SwitchAccount.vue') },
  { path: '/orders/:id', component: () => import('./views/Order.vue') },
  { path: '/profile', component: () => import('./views/Profile.vue') },
  { path: '/profile/address/new', component: () => import('./views/AddressForm.vue') },
  { path: '/profile/address/:id/edit', component: () => import('./views/AddressForm.vue') },
  { path: '/policy/after-sale', component: () => import('./views/AfterSalePolicy.vue') },
  { path: '/policy/privacy', component: () => import('./views/PrivacyPolicy.vue') },
  // Admin routes
  {
    path: '/admin',
    component: () => import('./views/admin/AdminLayout.vue'),
    meta: { requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', component: () => import('./views/admin/AdminDashboard.vue') },
      { path: 'users', component: () => import('./views/admin/AdminUsers.vue') },
      { path: 'products', component: () => import('./views/admin/AdminProducts.vue') },
      { path: 'orders', component: () => import('./views/admin/AdminOrders.vue') },
      { path: 'reports', component: () => import('./views/admin/AdminReports.vue') },
      { path: 'after-sale', component: () => import('./views/admin/AdminAfterSale.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') {
    return '/login'
  }
  // Admin guard
  if (to.meta.requiresAdmin) {
    const role = localStorage.getItem('role')
    if (role !== 'ADMIN') {
      return '/'
    }
  }
})

export default router
