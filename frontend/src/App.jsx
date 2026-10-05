import { Navigate, Route, Routes } from 'react-router'
import MainLayout from './components/MainLayout.jsx'
import GamesPage from './pages/GamesPage.jsx'
import { MAIN_ROUTES } from './routes.js'

function App() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route index element={<Navigate to="/games" replace />} />
        {MAIN_ROUTES.map(({ path }) => (
          <Route
            key={path}
            path={path}
            element={path === '/games' ? <GamesPage /> : <></>}
          />
        ))}
        <Route path="settings" element={<></>} />
        <Route path="*" element={<></>} />
      </Route>
    </Routes>
  )
}

export default App
