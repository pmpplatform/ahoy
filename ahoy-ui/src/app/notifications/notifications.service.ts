/*
 * Copyright  2020 LSD Information Technology (Pty) Ltd
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import {EventEmitter, Injectable} from '@angular/core';
import {LoggerService} from '../util/logger.service';
import {Notification} from './notification';

@Injectable({
  providedIn: 'root'
})
export class NotificationsService {
  public notifications = new EventEmitter<Notification>();
  public progress = false;

  constructor(private log: LoggerService) {
  }

  public showProgress(progress: boolean) {
    this.progress = progress;
  }

  public notification(notification: Notification) {
    this.notifications.emit(notification);
  }
}
