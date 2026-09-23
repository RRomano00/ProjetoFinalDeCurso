import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
// Import só pelo efeito colateral: registra o ouvinte de beforeinstallprompt
// antes do bootstrap. O Chrome dispara o evento uma vez e cedo — se o serviço
// só carregasse junto com a rota, o convite de instalação nunca apareceria.
import './app/services/local/install.service';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
