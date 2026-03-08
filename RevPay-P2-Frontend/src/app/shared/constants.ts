export const environment = {
  production: false,
  apiUrl: window.location.hostname === 'localhost' ? 'http://localhost:8080/api/v1' : `http://${window.location.hostname}:8080/api/v1`
};
