import { useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { MAIN_ROUTES } from '../routes.js'

function TopNavigation() {
  const { pathname } = useLocation()
  const [openMenuPath, setOpenMenuPath] = useState(null)
  const menuButtonRef = useRef(null)
  const isMenuOpen = openMenuPath === pathname

  function handleCloseMenu() {
    setOpenMenuPath(null)
  }

  function handleToggleMenu() {
    setOpenMenuPath(isMenuOpen ? null : pathname)
  }

  function handleKeyDown(event) {
    if (event.key === 'Escape' && isMenuOpen) {
      handleCloseMenu()
      menuButtonRef.current?.focus()
    }
  }

  return (
    <header className="app-header" onKeyDown={handleKeyDown}>
      <div className="header-content page-container">
        <Link className="app-brand" to="/games" onClick={handleCloseMenu}>
          <span className="brand-icon" aria-hidden="true">
            🏈
          </span>
          <span className="brand-text">
            <span className="brand-title">GameSense</span>
            <span className="brand-subtitle">CFB Predictions</span>
          </span>
        </Link>

        <button
          ref={menuButtonRef}
          className="menu-toggle"
          type="button"
          aria-expanded={isMenuOpen}
          aria-controls="primary-navigation"
          onClick={handleToggleMenu}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path
              d={
                isMenuOpen ? 'm6 6 12 12M6 18 18 6' : 'M4 6h16M4 12h16M4 18h16'
              }
            />
          </svg>
          <span>{isMenuOpen ? 'Close' : 'Menu'}</span>
        </button>

        <nav
          id="primary-navigation"
          className={`primary-navigation${isMenuOpen ? ' is-open' : ''}`}
          aria-label="Main navigation"
        >
          <ul className="navigation-list">
            {MAIN_ROUTES.map(({ path, title }) => (
              <li key={path}>
                <NavLink
                  className="navigation-link"
                  to={path}
                  onClick={handleCloseMenu}
                >
                  {title}
                </NavLink>
              </li>
            ))}
            <li className="settings-item">
              <NavLink
                className="navigation-link settings-link"
                to="/settings"
                aria-label="Settings"
                onClick={handleCloseMenu}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                  <path d="m9 3-.6 2.4-2 .9-2.2-.7-2 3.4 1.7 1.8v2.4l-1.7 1.8 2 3.4 2.2-.7 2 .9L9 21h4l.6-2.4 2-.9 2.2.7 2-3.4-1.7-1.8v-2.4L19.8 9l-2-3.4-2.2.7-2-.9L13 3Z" />
                  <circle cx="11" cy="12" r="3" />
                </svg>
                <span className="settings-label">Settings</span>
              </NavLink>
            </li>
          </ul>
        </nav>
      </div>
    </header>
  )
}

export default TopNavigation
