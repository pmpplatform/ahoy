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

import {Component, Input, OnInit} from '@angular/core';
import {Application, ApplicationVersion} from '../application';
import {ApplicationService} from '../application.service';
import {ConfirmationService} from 'primeng/api';

@Component({
  selector: 'app-application-versions',
  templateUrl: './application-versions.component.html',
  styleUrls: ['./application-versions.component.scss']
})
export class ApplicationVersionsComponent implements OnInit {
  @Input() application: Application;

  constructor(private applicationService: ApplicationService,
              private confirmationService: ConfirmationService) {
  }

  ngOnInit() {
  }

  removeApplicationVersion(event: Event, applicationVersion: ApplicationVersion) {
    this.confirmationService.confirm({
      target: event.target,
      message: `Are you sure you want to remove version ${applicationVersion.version} from ${this.application.name}?`,
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.applicationService.deleteVersion(applicationVersion)
          .subscribe(() => {
            const index = this.application.applicationVersions.indexOf(applicationVersion);
            if (index > -1) {
              this.application.applicationVersions.splice(index, 1);
            }
          });
      }
    });
  }

  lastVersionId() {
    const length = this.application.applicationVersions.length;
    if (length > 0) {
      return this.application.applicationVersions[length - 1].id;
    }
    return -1;
  }
}
