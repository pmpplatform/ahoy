/*
 * Copyright  2021 LSD Information Technology (Pty) Ltd
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

import {Component, OnInit} from '@angular/core';
import {GitSettings} from './git-settings';
import {GitSettingsService} from './git-settings.service';
import {Notification} from '../../notifications/notification';
import {NotificationsService} from '../../notifications/notifications.service';
import {AppBreadcrumbService} from '../../app.breadcrumb.service';

@Component({
  selector: 'app-git-settings',
  templateUrl: './git-settings.component.html',
  styleUrls: ['./git-settings.component.scss']
})
export class GitSettingsComponent implements OnInit {
  gitSettings: GitSettings;
  hideGitPassword = true;

  constructor(private gitSettingsService: GitSettingsService,
              private notificationsService: NotificationsService,
              private breadcrumbService: AppBreadcrumbService) {
    this.breadcrumbService.setItems([
      {label: 'settings'},
      {label: 'git'}
    ]);
  }

  ngOnInit(): void {
    this.gitSettingsService.get()
      .subscribe((gitSettings) => {
        this.gitSettings = gitSettings;
      });
  }

  save() {
    const notification = new Notification('Saved git settings');
    this.gitSettingsService.save(this.gitSettings)
      .subscribe(() => this.notificationsService.notification(notification));
  }

  test() {
    this.gitSettingsService.testConnection(this.gitSettings).subscribe();
  }
}
