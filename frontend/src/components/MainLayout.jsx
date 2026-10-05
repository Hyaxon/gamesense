import { Outlet } from 'react-router'
import TopNavigation from './TopNavigation.jsx'

function MainLayout() {
  return (
    <div className="app-layout">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <TopNavigation />
      <main
        id="main-content"
        className="main-content page-container"
        tabIndex={-1}
      >
        <Outlet />
      </main>
    </div>
  )
}

export default MainLayout
