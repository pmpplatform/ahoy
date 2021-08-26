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
import {EnvironmentRelease} from '../environment-release';

@Component({
  selector: 'app-environment-release-deployment-status',
  templateUrl: './environment-release-deployment-status.component.html',
  styleUrls: ['./environment-release-deployment-status.component.scss']
})
export class EnvironmentReleaseDeploymentStatusComponent implements OnInit {
  @Input() environmentRelease: EnvironmentRelease;

  constructor() {
  }

  ngOnInit(): void {
  }

}
