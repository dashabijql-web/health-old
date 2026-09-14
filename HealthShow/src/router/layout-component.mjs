// Keep one shared async Layout component identity across top-level route records.
// Recreating the loader in each route module makes Vue Router remount the
// sidebar/navbar when switching between feature areas.
const Layout = () => import('@/layout/index.vue')

export default Layout
